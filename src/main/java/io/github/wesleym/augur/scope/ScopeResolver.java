package io.github.wesleym.augur.scope;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;
import io.github.wesleym.augur.stmt.StatementSpan;
import io.github.wesleym.augur.stmt.StatementSplitter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Resolves table-like sources, aliases, CTEs, and child query scopes from one SQL statement. */
public final class ScopeResolver {
	public static final int DEFAULT_MAX_DEPTH = 6;

	private final Dialect dialect;
	private final int maxDepth;

	public ScopeResolver(Dialect dialect) {
		this(dialect, DEFAULT_MAX_DEPTH);
	}

	public ScopeResolver(Dialect dialect, int maxDepth) {
		this.dialect = dialect == null ? Dialects.ANSI : dialect;
		this.maxDepth = Math.max(0, maxDepth);
	}

	public static ResolvedScope resolve(String sql, int caretOffset, Dialect dialect) {
		return new ScopeResolver(dialect).resolve(sql, caretOffset);
	}

	public static ResolvedScope resolveStatement(String sql, Dialect dialect) {
		return new ScopeResolver(dialect).resolveStatement(sql);
	}

	public ResolvedScope resolve(String sqlText, int caretOffset) {
		String sql = text(sqlText);
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
		StatementSpan statement = StatementSplitter.statementAt(sql, caretOffset, dialect);
		return resolveTokens(statement.tokens(), 0, List.of());
	}

	public ResolvedScope resolveStatement(String sqlText) {
		return resolveTokens(SqlLexer.lex(sqlText, dialect), 0, List.of());
	}

	private ResolvedScope resolveTokens(List<Token> tokens, int depth, List<ScopeSource> inheritedSources) {
		if (depth > maxDepth) {
			return new ResolvedScope(depth, true, List.of(), inheritedSources, List.of(), List.of());
		}
		ParsedCtes parsedCtes = readCtes(tokens);
		Set<String> cteNames = cteNames(parsedCtes.drafts(), inheritedSources);
		ScanResult scanned = scanSources(tokens, parsedCtes.mainStart(), cteNames, depth);
		List<ScopeSource> childInheritance = visibleForChildren(scanned.sources(), inheritedSources);
		List<CteBinding> ctes = bindCtes(tokens, parsedCtes.drafts(), depth, inheritedSources);
		List<ResolvedScope> children = new ArrayList<>();
		for (CteBinding cte : ctes) {
			children.add(cte.queryScope());
		}
		for (QueryRange childRange : scanned.childRanges()) {
			children.add(resolveTokens(tokens.subList(childRange.startIndex(), childRange.endIndex()), depth + 1,
					childInheritance));
		}
		boolean truncated = children.stream().anyMatch(ResolvedScope::truncated);
		return new ResolvedScope(depth, truncated, scanned.sources(), inheritedSources, ctes, children);
	}

	private List<CteBinding> bindCtes(List<Token> tokens, List<CteDraft> drafts, int depth,
			List<ScopeSource> inheritedSources) {
		if (drafts.isEmpty()) {
			return List.of();
		}
		List<CteBinding> out = new ArrayList<>(drafts.size());
		List<ScopeSource> cteSources = new ArrayList<>(inheritedSources);
		for (CteDraft draft : drafts) {
			ResolvedScope queryScope = resolveTokens(tokens.subList(draft.queryStart(), draft.queryEnd()), depth + 1,
					cteSources);
			out.add(new CteBinding(draft.name(), draft.columns(), queryScope, draft.startOffset(), draft.endOffset()));
			cteSources.add(new ScopeSource(draft.name(), draft.name(), SourceKind.CTE, draft.startOffset(),
					draft.endOffset(), depth));
		}
		return List.copyOf(out);
	}

	private ScanResult scanSources(List<Token> tokens, int start, Set<String> cteNames, int depth) {
		List<ScopeSource> sources = new ArrayList<>();
		List<QueryRange> childRanges = new ArrayList<>();
		boolean deleteStatement = firstKeyword(tokens, start, "DELETE");
		for (int i = start; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				int close = findClose(tokens, i);
				int childEnd = close < 0 ? tokens.size() : close;
				if (containsQueryHead(tokens, i + 1, childEnd)) {
					childRanges.add(new QueryRange(i + 1, childEnd));
					i = childEnd;
				}
				continue;
			}
			if (!isKeyword(token)) {
				continue;
			}
			SourceRead read = readSourceAfterKeyword(tokens, start, i, token, deleteStatement, sources, cteNames,
					depth);
			if (read.hasSource()) {
				sources.add(read.source());
				if (read.childRange() != null) {
					childRanges.add(read.childRange());
				}
				int next = read.nextIndex();
				if (token.isText("FROM") || token.isText("UPDATE")) {
					next = readCommaSeparatedSources(tokens, next, cteNames, depth, sources, childRanges);
				}
				i = Math.max(i, next - 1);
			}
		}
		return new ScanResult(List.copyOf(sources), List.copyOf(childRanges));
	}

	private SourceRead readSourceAfterKeyword(List<Token> tokens, int start, int keywordIndex, Token keyword,
			boolean deleteStatement, List<ScopeSource> sources, Set<String> cteNames, int depth) {
		if (keyword.isText("FROM") || keyword.isText("JOIN")) {
			SourceKind kind = keyword.isText("FROM") && deleteStatement && sources.isEmpty()
					? SourceKind.DELETE_TARGET : SourceKind.TABLE;
			return readSource(tokens, keywordIndex + 1, kind, cteNames, depth);
		}
		if (keyword.isText("UPDATE")) {
			return readSource(tokens, keywordIndex + 1, SourceKind.UPDATE_TARGET, cteNames, depth);
		}
		if (keyword.isText("INTO") && seenKeyword(tokens, start, keywordIndex, "INSERT")) {
			return readSource(tokens, keywordIndex + 1, SourceKind.INSERT_TARGET, cteNames, depth);
		}
		return SourceRead.none(keywordIndex + 1);
	}

	private SourceRead readSource(List<Token> tokens, int index, SourceKind requestedKind, Set<String> cteNames,
			int depth) {
		int i = skipNoise(tokens, index);
		if (i >= tokens.size()) {
			return SourceRead.none(index);
		}
		Token first = tokens.get(i);
		if (first.isText("(")) {
			int close = findClose(tokens, i);
			int closeIndex = close < 0 ? tokens.size() : close;
			AliasRead alias = readAlias(tokens, closeIndex + 1);
			int sourceEnd = alias.endOffset() >= 0 ? alias.endOffset() : first.end();
			String aliasText = alias.alias();
			ScopeSource source = new ScopeSource(aliasText, aliasText, SourceKind.DERIVED_TABLE, first.start(),
					sourceEnd, depth);
			return new SourceRead(source, alias.nextIndex(), new QueryRange(i + 1, closeIndex));
		}
		if (!isNameToken(first)) {
			return SourceRead.none(i);
		}
		QualifiedName qualified = readQualifiedName(tokens, i);
		AliasRead alias = readAlias(tokens, qualified.nextIndex());
		String name = qualified.name();
		String aliasText = alias.alias().isEmpty() ? lastPart(name) : alias.alias();
		SourceKind kind = requestedKind == SourceKind.TABLE && cteNames.contains(key(lastPart(name)))
				? SourceKind.CTE : requestedKind;
		ScopeSource source = new ScopeSource(name, aliasText, kind, first.start(),
				alias.endOffset() >= 0 ? alias.endOffset() : qualified.endOffset(), depth);
		return new SourceRead(source, alias.nextIndex(), null);
	}

	/**
	 * Reads old-style comma-separated table lists such as {@code from a, b c}
	 * (implicit cross joins), appending each additional source. Returns the token
	 * index just past the last source read.
	 */
	private int readCommaSeparatedSources(List<Token> tokens, int index, Set<String> cteNames, int depth,
			List<ScopeSource> sources, List<QueryRange> childRanges) {
		int next = index;
		while (next < tokens.size() && tokens.get(next).isText(",")) {
			SourceRead more = readSource(tokens, next + 1, SourceKind.TABLE, cteNames, depth);
			if (!more.hasSource()) {
				break;
			}
			sources.add(more.source());
			if (more.childRange() != null) {
				childRanges.add(more.childRange());
			}
			if (more.nextIndex() <= next) {
				break;
			}
			next = more.nextIndex();
		}
		return next;
	}

	private ParsedCtes readCtes(List<Token> tokens) {
		if (tokens.isEmpty() || !tokens.get(0).isText("WITH")) {
			return new ParsedCtes(List.of(), 0);
		}
		int i = 1;
		if (i < tokens.size() && tokens.get(i).isText("RECURSIVE")) {
			i++;
		}
		List<CteDraft> drafts = new ArrayList<>();
		while (i < tokens.size() && isNameToken(tokens.get(i))) {
			Token nameToken = tokens.get(i);
			String name = identifierText(nameToken);
			int startOffset = nameToken.start();
			i++;
			List<String> columns = List.of();
			if (i < tokens.size() && tokens.get(i).isText("(")) {
				int close = findClose(tokens, i);
				if (close < 0) {
					return new ParsedCtes(drafts, i);
				}
				columns = readColumnList(tokens, i + 1, close);
				i = close + 1;
			}
			if (i >= tokens.size() || !tokens.get(i).isText("AS")) {
				return new ParsedCtes(drafts, i);
			}
			i++;
			if (i >= tokens.size() || !tokens.get(i).isText("(")) {
				return new ParsedCtes(drafts, i);
			}
			int close = findClose(tokens, i);
			int queryEnd = close < 0 ? tokens.size() : close;
			int endOffset = close < 0 ? tokens.get(tokens.size() - 1).end() : tokens.get(close).end();
			drafts.add(new CteDraft(name, columns, i + 1, queryEnd, startOffset, endOffset));
			i = close < 0 ? tokens.size() : close + 1;
			if (i < tokens.size() && tokens.get(i).isText(",")) {
				i++;
			} else {
				break;
			}
		}
		return new ParsedCtes(List.copyOf(drafts), i);
	}

	private static List<String> readColumnList(List<Token> tokens, int start, int end) {
		List<String> columns = new ArrayList<>();
		for (int i = start; i < end; i++) {
			Token token = tokens.get(i);
			if (isNameToken(token)) {
				columns.add(identifierText(token));
			}
		}
		return List.copyOf(columns);
	}

	private static QualifiedName readQualifiedName(List<Token> tokens, int start) {
		StringBuilder name = new StringBuilder(identifierText(tokens.get(start)));
		int endOffset = tokens.get(start).end();
		int i = start + 1;
		while (i + 1 < tokens.size() && tokens.get(i).isText(".") && isNameToken(tokens.get(i + 1))) {
			name.append('.').append(identifierText(tokens.get(i + 1)));
			endOffset = tokens.get(i + 1).end();
			i += 2;
		}
		return new QualifiedName(name.toString(), i, endOffset);
	}

	private static AliasRead readAlias(List<Token> tokens, int index) {
		// An alias follows the source name directly (optionally after AS). A comma
		// ends the source, so it must never be skipped here or the next table in a
		// comma-separated FROM list would be swallowed as this source's alias.
		int i = index;
		if (i < tokens.size() && tokens.get(i).isText("AS")) {
			i++;
		}
		if (i < tokens.size() && isAliasToken(tokens.get(i))) {
			Token alias = tokens.get(i);
			return new AliasRead(identifierText(alias), i + 1, alias.end());
		}
		return new AliasRead("", index, -1);
	}

	private static int skipNoise(List<Token> tokens, int index) {
		int i = index;
		while (i < tokens.size() && (tokens.get(i).isText(",") || tokens.get(i).isText("LATERAL"))) {
			i++;
		}
		return i;
	}

	private static int findClose(List<Token> tokens, int open) {
		int depth = 0;
		for (int i = open; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				depth++;
			} else if (token.isText(")")) {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private static boolean containsQueryHead(List<Token> tokens, int start, int end) {
		for (int i = start; i < end; i++) {
			Token token = tokens.get(i);
			if (token.isText("SELECT") || token.isText("WITH")) {
				return true;
			}
		}
		return false;
	}

	private static boolean firstKeyword(List<Token> tokens, int start, String keyword) {
		for (int i = start; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (isKeyword(token)) {
				return token.isText(keyword);
			}
		}
		return false;
	}

	private static boolean seenKeyword(List<Token> tokens, int start, int end, String keyword) {
		for (int i = start; i < end; i++) {
			if (tokens.get(i).isText(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> cteNames(List<CteDraft> drafts, List<ScopeSource> inheritedSources) {
		if (drafts.isEmpty() && inheritedSources.isEmpty()) {
			return Set.of();
		}
		Set<String> names = new LinkedHashSet<>();
		for (CteDraft draft : drafts) {
			names.add(key(draft.name()));
		}
		for (ScopeSource inherited : inheritedSources) {
			if (inherited.kind() == SourceKind.CTE) {
				names.add(key(inherited.name()));
			}
		}
		return Set.copyOf(names);
	}

	private static List<ScopeSource> visibleForChildren(List<ScopeSource> sources, List<ScopeSource> inherited) {
		if (sources.isEmpty()) {
			return inherited;
		}
		List<ScopeSource> out = new ArrayList<>(sources.size() + inherited.size());
		out.addAll(sources);
		out.addAll(inherited);
		return List.copyOf(out);
	}

	private static boolean isKeyword(Token token) {
		return token.kind() == TokenKind.KEYWORD;
	}

	private static boolean isNameToken(Token token) {
		return token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.QUOTED_IDENTIFIER
				|| token.kind() == TokenKind.KEYWORD;
	}

	private static boolean isAliasToken(Token token) {
		return token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.QUOTED_IDENTIFIER;
	}

	private static String identifierText(Token token) {
		String text = token.text();
		if (token.kind() == TokenKind.QUOTED_IDENTIFIER && text.length() >= 2) {
			return text.substring(1, text.length() - 1).replace("]]", "]").replace("\"\"", "\"")
					.replace("``", "`");
		}
		return text;
	}

	private static String lastPart(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(dot + 1);
	}

	private static String key(String value) {
		return text(value).toLowerCase(Locale.ROOT);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private record ParsedCtes(List<CteDraft> drafts, int mainStart) { }
	private record CteDraft(String name, List<String> columns, int queryStart, int queryEnd, int startOffset,
			int endOffset) { }
	private record QueryRange(int startIndex, int endIndex) { }
	private record ScanResult(List<ScopeSource> sources, List<QueryRange> childRanges) { }
	private record SourceRead(ScopeSource source, int nextIndex, QueryRange childRange) {
		private boolean hasSource() {
			return source != null;
		}

		private static SourceRead none(int nextIndex) {
			return new SourceRead(null, nextIndex, null);
		}
	}
	private record QualifiedName(String name, int nextIndex, int endOffset) { }
	private record AliasRead(String alias, int nextIndex, int endOffset) { }
}

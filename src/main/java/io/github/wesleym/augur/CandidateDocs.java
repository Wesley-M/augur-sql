package io.github.wesleym.augur;

/** Default plaintext and HTML renderers for {@link CandidateDoc}. */
public final class CandidateDocs {
	private CandidateDocs() { }

	public static String plainText(CandidateDoc doc) {
		CandidateDoc value = doc == null ? CandidateDoc.empty() : doc;
		StringBuilder out = new StringBuilder();
		appendLine(out, value.qualifiedName());
		appendLine(out, value.type());
		if (!value.badges().isEmpty()) {
			appendLine(out, "Badges: " + String.join(", ", value.badges()));
		}
		appendLine(out, value.referencedTable().isEmpty() ? "" : "References: " + value.referencedTable());
		if (value.nullFraction() != null) {
			appendLine(out, "Nulls: " + percent(value.nullFraction()));
		}
		if (value.distinctCount() != null) {
			appendLine(out, "Distinct: " + value.distinctCount());
		}
		if (!value.topValues().isEmpty()) {
			appendLine(out, "Top values:");
			for (ValueShare share : value.topValues()) {
				appendLine(out, "  " + share.value() + " - " + percent(share.share())
						+ (share.approximate() ? " approx" : ""));
			}
		}
		return out.toString().stripTrailing();
	}

	public static String html(CandidateDoc doc) {
		CandidateDoc value = doc == null ? CandidateDoc.empty() : doc;
		StringBuilder out = new StringBuilder("<div class=\"augur-doc\">");
		if (!value.qualifiedName().isEmpty()) {
			out.append("<div class=\"augur-doc-title\">").append(escape(value.qualifiedName())).append("</div>");
		}
		if (!value.type().isEmpty()) {
			out.append("<div class=\"augur-doc-type\">").append(escape(value.type())).append("</div>");
		}
		if (!value.badges().isEmpty()) {
			out.append("<ul class=\"augur-doc-badges\">");
			for (String badge : value.badges()) {
				out.append("<li>").append(escape(badge)).append("</li>");
			}
			out.append("</ul>");
		}
		if (!value.referencedTable().isEmpty()) {
			out.append("<div class=\"augur-doc-reference\">References ")
					.append(escape(value.referencedTable()))
					.append("</div>");
		}
		if (value.nullFraction() != null || value.distinctCount() != null) {
			out.append("<dl class=\"augur-doc-stats\">");
			if (value.nullFraction() != null) {
				out.append("<dt>Nulls</dt><dd>").append(percent(value.nullFraction())).append("</dd>");
			}
			if (value.distinctCount() != null) {
				out.append("<dt>Distinct</dt><dd>").append(value.distinctCount()).append("</dd>");
			}
			out.append("</dl>");
		}
		if (!value.topValues().isEmpty()) {
			out.append("<ol class=\"augur-doc-values\">");
			for (ValueShare share : value.topValues()) {
				out.append("<li><span>")
						.append(escape(share.value()))
						.append("</span> <small>")
						.append(percent(share.share()));
				if (share.approximate()) {
					out.append(" approx");
				}
				out.append("</small></li>");
			}
			out.append("</ol>");
		}
		return out.append("</div>").toString();
	}

	private static void appendLine(StringBuilder out, String line) {
		if (line != null && !line.isEmpty()) {
			if (!out.isEmpty()) {
				out.append('\n');
			}
			out.append(line);
		}
	}

	private static String percent(double value) {
		return Math.round(value * 100.0) + "%";
	}

	private static String escape(String value) {
		String text = Catalog.text(value);
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				case '\'' -> out.append("&#39;");
				default -> out.append(c);
			}
		}
		return out.toString();
	}
}

package io.github.wesleym.augur.context;

import java.util.ArrayList;
import java.util.List;

/** Completion context at the caret. */
public sealed interface Context permits Context.TableRef, Context.ColumnRef, Context.JoinTarget,
		Context.OnCondition, Context.InsertColumns, Context.SetAssignment, Context.GroupByRef,
		Context.OrderByRef, Context.ValueLiteral, Context.StatementHead, Context.ExpressionRef, Context.Quiet {
	default String prefix() {
		return "";
	}

	record TableRef(String prefix) implements Context {
		public TableRef { prefix = text(prefix); }
	}

	record ColumnRef(String prefix, String qualifier) implements Context {
		public ColumnRef(String prefix) {
			this(prefix, "");
		}

		public ColumnRef {
			prefix = text(prefix);
			qualifier = text(qualifier);
		}
	}

	record JoinTarget(String prefix) implements Context {
		public JoinTarget { prefix = text(prefix); }
	}

	record OnCondition(String prefix) implements Context {
		public OnCondition { prefix = text(prefix); }
	}

	record InsertColumns(String prefix, boolean openParenTyped) implements Context {
		public InsertColumns(String prefix) {
			this(prefix, false);
		}

		public InsertColumns {
			prefix = text(prefix);
		}
	}

	record SetAssignment(String prefix) implements Context {
		public SetAssignment { prefix = text(prefix); }
	}

	record GroupByRef(String prefix, List<String> expressions) implements Context {
		public GroupByRef(String prefix) {
			this(prefix, List.of());
		}

		public GroupByRef {
			prefix = text(prefix);
			if (expressions == null) {
				expressions = List.of();
			} else {
				ArrayList<String> normalized = new ArrayList<>(expressions.size());
				for (String expression : expressions) {
					normalized.add(text(expression));
				}
				expressions = List.copyOf(normalized);
			}
		}
	}

	record OrderByRef(String prefix) implements Context {
		public OrderByRef { prefix = text(prefix); }
	}

	record ValueLiteral(String prefix, String qualifier, String column) implements Context {
		public ValueLiteral(String prefix) {
			this(prefix, "", "");
		}

		public ValueLiteral {
			prefix = text(prefix);
			qualifier = text(qualifier);
			column = text(column);
		}
	}

	record StatementHead(String prefix) implements Context {
		public StatementHead { prefix = text(prefix); }
	}

	record ExpressionRef(String prefix) implements Context {
		public ExpressionRef { prefix = text(prefix); }
	}

	record Quiet(String reason) implements Context {
		public Quiet { reason = text(reason); }
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}

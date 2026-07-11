package io.github.wesleym.augur.bench;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.Completion;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.Provenance;
import io.github.wesleym.augur.TypeFamily;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic latency guard for large-catalog completion paths. */
public final class CompletionBenchmark {
	private static final int TABLES = 5_000;
	private static final int COLUMNS_PER_TABLE = 12;
	private static final int WARMUP = 20;
	private static final int ITERATIONS = 80;
	private static final double DEFAULT_MAX_P99_MILLIS = 500.0;

	private CompletionBenchmark() { }

	public static void main(String[] args) {
		double maxP99Millis = Double.parseDouble(System.getProperty("augur.benchmark.maxP99Millis",
				String.valueOf(DEFAULT_MAX_P99_MILLIS)));
		Augur augur = Augur.create(catalog(), Dialects.POSTGRES);

		Map<String, Case> cases = new LinkedHashMap<>();
		cases.put("table-prefix", Case.atEnd("select * from table_049"));
		cases.put("join-prefix", Case.atEnd("select * from table_0001 t join table_049"));
		cases.put("qualified-column", Case.atCaret("select t.column_ from table_0001 t", "select t.column_"));
		cases.put("insert-scaffold", Case.atEnd("insert into table_0001 "));

		for (Map.Entry<String, Case> entry : cases.entrySet()) {
			Result result = measure(augur, entry.getValue());
			System.out.printf("%s: min=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%n",
					entry.getKey(), result.minMillis(), result.p50Millis(), result.p95Millis(),
					result.p99Millis(), result.maxMillis());
			if (result.p99Millis() > maxP99Millis) {
				throw new IllegalStateException(entry.getKey() + " p99 " + result.p99Millis()
						+ "ms exceeded " + maxP99Millis + "ms");
			}
		}
	}

	private static Result measure(Augur augur, Case benchmarkCase) {
		for (int i = 0; i < WARMUP; i++) {
			runOnce(augur, benchmarkCase);
		}
		long[] nanos = new long[ITERATIONS];
		for (int i = 0; i < ITERATIONS; i++) {
			long start = System.nanoTime();
			runOnce(augur, benchmarkCase);
			nanos[i] = System.nanoTime() - start;
		}
		Arrays.sort(nanos);
		return new Result(nanos);
	}

	private static void runOnce(Augur augur, Case benchmarkCase) {
		Completion completion = augur.complete(benchmarkCase.sql(), benchmarkCase.caretOffset());
		if (completion.isEmpty()) {
			throw new IllegalStateException("benchmark query produced no candidates: " + benchmarkCase.sql());
		}
	}

	private static Catalog catalog() {
		Catalog.Builder builder = Catalog.builder();
		builder.table("patient", table -> table
				.column("id", "integer", column -> column.primaryKey().typeFamily(TypeFamily.INTEGER))
				.column("email", "varchar", column -> column.role(ColumnRole.SENSITIVE)));
		for (int table = 0; table < TABLES; table++) {
			int index = table;
			builder.table(tableName(index), draft -> {
				draft.column("id", "integer", column -> column.primaryKey().typeFamily(TypeFamily.INTEGER));
				draft.column("patient_id", "integer",
						column -> column.typeFamily(TypeFamily.INTEGER)
								.referencing("patient", "id", Provenance.INFERRED));
				for (int column = 2; column < COLUMNS_PER_TABLE; column++) {
					String type = column % 3 == 0 ? "timestamp" : column % 3 == 1 ? "varchar" : "decimal";
					TypeFamily family = column % 3 == 0 ? TypeFamily.TEMPORAL
							: column % 3 == 1 ? TypeFamily.TEXT : TypeFamily.DECIMAL;
					draft.column("column_" + column, type, c -> c.typeFamily(family));
				}
				draft.rowCount(10_000 + index);
			});
		}
		return builder.build();
	}

	private static String tableName(int index) {
		return "table_" + String.format("%04d", index);
	}

	private record Case(String sql, int caretOffset) {
		private static Case atEnd(String sql) {
			return new Case(sql, sql.length());
		}

		private static Case atCaret(String sql, String caretPrefix) {
			return new Case(sql, caretPrefix.length());
		}
	}

	private record Result(long[] nanos) {
		private double minMillis() {
			return millis(nanos[0]);
		}

		private double p50Millis() {
			return percentile(0.50);
		}

		private double p95Millis() {
			return percentile(0.95);
		}

		private double p99Millis() {
			return percentile(0.99);
		}

		private double maxMillis() {
			return millis(nanos[nanos.length - 1]);
		}

		private double percentile(double percentile) {
			int index = Math.min(nanos.length - 1, (int) Math.ceil(percentile * nanos.length) - 1);
			return millis(nanos[index]);
		}

		private static double millis(long nanos) {
			return nanos / 1_000_000.0;
		}
	}
}

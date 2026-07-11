package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandidateTest {
	@Test
	void candidateNormalizesTextAndDefensivelyCopiesMatches() {
		int[] matches = {0, 2};

		Candidate candidate = new Candidate(new CandidateKind.Column(), null, null, "status", 6, matches, null);
		matches[0] = 99;

		assertEquals("", candidate.display());
		assertEquals("", candidate.detail());
		assertArrayEquals(new int[] {0, 2}, candidate.matchedChars());
		assertNotSame(matches, candidate.matchedChars());
		assertEquals(CandidateDoc.empty(), candidate.doc());
	}

	@Test
	void candidateKeepsExplicitDocAndRequiresKind() {
		CandidateDoc doc = new CandidateDoc("appointment.status", "varchar", List.of("payload"), "", 0.1, 3L,
				List.of());

		Candidate candidate = new Candidate(new CandidateKind.Value(), "open", "value", "'open'", 6, null, doc);

		assertEquals(doc, candidate.doc());
		assertThrows(NullPointerException.class, () -> new Candidate(null, "x", "", "x", 1, null, null));
	}

	@Test
	void candidateRejectsInvalidCaret() {
		assertThrows(IllegalArgumentException.class,
				() -> new Candidate(new CandidateKind.Keyword(), "select", "", "select", -1, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> new Candidate(new CandidateKind.Keyword(), "select", "", "select", 7, null, null));
	}

	@Test
	void candidateDocDropsNullAndBlankCollections() {
		CandidateDoc doc = new CandidateDoc(null, null, Arrays.asList("pk", null, "", "fk"), null, null, null,
				Arrays.asList(null, new ValueShare("open", 0.25, true)));

		assertEquals("", doc.qualifiedName());
		assertEquals(List.of("pk", "fk"), doc.badges());
		assertEquals(1, doc.topValues().size());
	}

	@Test
	void candidateDocAcceptsNullCollections() {
		CandidateDoc doc = new CandidateDoc("patient.email", "varchar", null, "patient", null, null, null);

		assertEquals(List.of(), doc.badges());
		assertEquals(List.of(), doc.topValues());
	}

	@Test
	void candidateDocBuilderAndInstanceRenderersAreConvenient() {
		CandidateDoc doc = CandidateDoc.builder()
				.qualifiedName("appointment.status")
				.type("varchar")
				.badge("value")
				.badges(Arrays.asList(null, "", "profiled"))
				.referencedTable("appointment")
				.nullFraction(0.20)
				.distinctCount(2)
				.topValue("open", 0.60)
				.topValue("closed", 0.30, true)
				.topValue(null)
				.build();
		CandidateDoc copy = doc.toBuilder().badge("extra").build();

		assertEquals(List.of("value", "profiled"), doc.badges());
		assertEquals(2, doc.topValues().size());
		assertEquals("appointment.status", doc.plainText().lines().findFirst().orElseThrow());
		assertEquals(CandidateDocs.html(doc), doc.html());
		assertEquals(List.of("value", "profiled", "extra"), copy.badges());
	}

	@Test
	void candidateDocsRenderPlainTextAndEscapedHtml() {
		CandidateDoc doc = new CandidateDoc("appointment.status", "varchar <text> & \"label\"",
				List.of("value", "approx"), "patient", 0.125, 3L,
				List.of(new ValueShare("can't <open> & \"closed\"", 0.62, true)));

		String plain = CandidateDocs.plainText(doc);
		String html = CandidateDocs.html(doc);

		assertEquals("", CandidateDocs.plainText(null));
		assertEquals("<div class=\"augur-doc\"></div>", CandidateDocs.html(null));
		assertEquals("""
				appointment.status
				varchar <text> & "label"
				Badges: value, approx
				References: patient
				Nulls: 13%
				Distinct: 3
				Top values:
				  can't <open> & "closed" - 62% approx""", plain);
		assertEquals("""
				<div class=\"augur-doc\"><div class=\"augur-doc-title\">appointment.status</div><div class=\"augur-doc-type\">varchar &lt;text&gt; &amp; &quot;label&quot;</div><ul class=\"augur-doc-badges\"><li>value</li><li>approx</li></ul><div class=\"augur-doc-reference\">References patient</div><dl class=\"augur-doc-stats\"><dt>Nulls</dt><dd>13%</dd><dt>Distinct</dt><dd>3</dd></dl><ol class=\"augur-doc-values\"><li><span>can&#39;t &lt;open&gt; &amp; &quot;closed&quot;</span> <small>62% approx</small></li></ol></div>""", html);
	}

	@Test
	void completionDefensivelyCopiesCandidates() {
		Candidate candidate = new Candidate(new CandidateKind.Table(), "patient", "", "patient", 7, null, null);
		Completion completion = new Completion(Arrays.asList(null, candidate), TextSpan.of(0, 0));

		assertEquals(List.of(candidate), completion.candidates());
		assertFalse(completion.isEmpty());
		assertEquals(candidate, completion.first().orElseThrow());
		assertEquals("patient", completion.firstEdit().orElseThrow().insertText());
		assertEquals("patientselect", completion.apply("select", candidate));
		assertEquals("patientselect", completion.applyFirst("select"));
		assertThrows(NullPointerException.class, () -> completion.edit(null));
		assertThrows(NullPointerException.class, () -> new Completion(null, null));
	}

	@Test
	void valueShareValidatesShare() {
		assertEquals("", new ValueShare(null, 0.0, false).value());
		assertThrows(IllegalArgumentException.class, () -> new ValueShare("bad", Double.NaN, false));
		assertThrows(IllegalArgumentException.class, () -> new ValueShare("bad", -0.1, false));
		assertThrows(IllegalArgumentException.class, () -> new ValueShare("bad", 1.1, false));
	}
}

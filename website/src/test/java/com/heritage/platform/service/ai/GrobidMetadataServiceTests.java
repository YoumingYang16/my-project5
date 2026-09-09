package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.GrobidMetadata;
import org.junit.jupiter.api.Test;

import com.heritage.platform.service.CrossrefMetadataService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GrobidMetadataServiceTests {

    private final CrossrefMetadataService crossref = mock(CrossrefMetadataService.class);
    private final GrobidMetadataService service = new GrobidMetadataService(crossref);

    @Test
    void removesAffiliationsCaptionAbstractAndReferenceYears() throws Exception {
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0">
                  <teiHeader>
                    <fileDesc>
                      <titleStmt><title>ARTimeTravel: Understanding Spatial Changes</title></titleStmt>
                      <publicationStmt><date/></publicationStmt>
                      <sourceDesc><biblStruct><analytic>
                        <author><persName><forename>Jiachen</forename><surname>Liang</surname></persName></author>
                        <author><persName><forename>Yue</forename><surname>Li</surname></persName></author>
                        <author><persName><forename>Gengyuan</forename><surname>Zeng</surname></persName></author>
                        <author><persName><forename>Yiping</forename><surname>Dong</surname></persName></author>
                        <author><affiliation><orgName>School of Advanced Technology University</orgName></affiliation></author>
                      </analytic></biblStruct></sourceDesc>
                    </fileDesc>
                    <profileDesc><abstract>Figure 1: The demonstration of ARTimeTravel and screenshots.</abstract></profileDesc>
                  </teiHeader>
                  <text><body>
                    <div><head>Introduction</head><p>Heritage visitors need an engaging way to understand how sites changed spatially and culturally over time.</p></div>
                    <div><head>References</head><p>A referenced method was published in 1996.</p><date type="published" when="1996">1996</date></div>
                  </body></text>
                </TEI>
                """;

        GrobidMetadata metadata = service.parseTei(tei);

        assertThat(metadata.authors()).containsExactly("Jiachen Liang", "Yue Li", "Gengyuan Zeng", "Yiping Dong");
        assertThat(metadata.abstractText()).startsWith("Heritage visitors need an engaging way");
        assertThat(metadata.abstractText()).doesNotStartWith("Figure 1");
        assertThat(metadata.year()).isNull();
        assertThat(metadata.warnings()).anyMatch(value -> value.contains("figure caption"));
    }

    @Test
    void acceptsPublicationYearOnlyFromHeaderPublicationMetadata() throws Exception {
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0">
                  <teiHeader><fileDesc>
                    <titleStmt><title>Paper</title></titleStmt>
                    <publicationStmt><date type="published" when="2025">2025</date></publicationStmt>
                    <sourceDesc><biblStruct/></sourceDesc>
                  </fileDesc></teiHeader>
                  <text><body><div><date type="published" when="1996">1996</date></div></body></text>
                </TEI>
                """;

        assertThat(service.parseTei(tei).year()).isEqualTo(2025);
    }

    @Test
    void preservesHeaderDoiForTheMandatoryCentralEnrichmentPass() throws Exception {
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0"><teiHeader><fileDesc>
                  <titleStmt><title>Paper</title></titleStmt><publicationStmt/>
                  <sourceDesc><biblStruct><analytic><idno type="DOI">10.1145/example</idno></analytic></biblStruct></sourceDesc>
                </fileDesc></teiHeader></TEI>
                """;

        GrobidMetadata metadata = service.parseTei(tei);

        assertThat(metadata.doi()).isEqualTo("10.1145/example");
        assertThat(metadata.year()).isNull();
        assertThat(metadata.authors()).isEmpty();
        verifyNoInteractions(crossref);
    }
}

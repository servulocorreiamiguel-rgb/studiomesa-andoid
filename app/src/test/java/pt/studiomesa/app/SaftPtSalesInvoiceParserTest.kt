package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SaftPtSalesInvoiceParserTest {
    @Test
    fun importsAllValidSalesDocumentsIndependentOfLoyaltyAndExcludesVoidedOrDuplicateSummaries() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <AuditFile xmlns="urn:OECD:StandardAuditFile-Tax:PT_1.04_01">
              <Header><AuditFileVersion>1.04_01</AuditFileVersion></Header>
              <SourceDocuments>
                <SalesInvoices>
                  <Invoice>
                    <InvoiceNo>FT A/1</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-01</InvoiceDate><InvoiceType>FT</InvoiceType>
                    <Line><ProductCode>BURG</ProductCode><Quantity>2</Quantity><UnitPrice>10.00</UnitPrice><Description>Hambúrguer</Description><DebitAmount>20.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>20.00</NetTotal><GrossTotal>24.60</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>FS A/2</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-02</InvoiceDate><InvoiceType>FS</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>8.00</UnitPrice><Description>Sopa</Description><DebitAmount>8.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>8.00</NetTotal><GrossTotal>9.84</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>NC A/3</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-03</InvoiceDate><InvoiceType>NC</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>10.00</UnitPrice><Description>Hambúrguer</Description><CreditAmount>10.00</CreditAmount></Line>
                    <DocumentTotals><NetTotal>10.00</NetTotal><GrossTotal>12.30</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>FT A/4</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>A</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-04</InvoiceDate><InvoiceType>FT</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>50.00</UnitPrice><Description>Documento anulado</Description><DebitAmount>50.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>50.00</NetTotal><GrossTotal>61.50</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>FT A/5</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>F</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-05</InvoiceDate><InvoiceType>FT</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>60.00</UnitPrice><Description>Documento substituído</Description><DebitAmount>60.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>60.00</NetTotal><GrossTotal>73.80</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>Resumo/6</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>R</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-06</InvoiceDate><InvoiceType>FT</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>90.00</UnitPrice><Description>Resumo de sistema integrado</Description><DebitAmount>90.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>90.00</NetTotal><GrossTotal>110.70</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>VD A/8</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2012-11-08</InvoiceDate><InvoiceType>VD</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>7.00</UnitPrice><Description>Venda a dinheiro</Description><DebitAmount>7.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>7.00</NetTotal><GrossTotal>8.61</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>TV A/9</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2012-11-09</InvoiceDate><InvoiceType>TV</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>6.00</UnitPrice><Description>Talao de venda</Description><DebitAmount>6.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>6.00</NetTotal><GrossTotal>7.38</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>TD A/10</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2012-11-10</InvoiceDate><InvoiceType>TD</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>5.00</UnitPrice><Description>Devolucao</Description><DebitAmount>5.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>5.00</NetTotal><GrossTotal>6.15</GrossTotal></DocumentTotals>
                  </Invoice>
                  <Invoice>
                    <InvoiceNo>FR A/7</InvoiceNo>
                    <DocumentStatus><InvoiceStatus>S</InvoiceStatus></DocumentStatus>
                    <InvoiceDate>2026-11-07</InvoiceDate><InvoiceType>FR</InvoiceType>
                    <Line><Quantity>1</Quantity><UnitPrice>12.00</UnitPrice><Description>Sumo</Description><DebitAmount>12.00</DebitAmount></Line>
                    <DocumentTotals><NetTotal>12.00</NetTotal><GrossTotal>14.76</GrossTotal></DocumentTotals>
                  </Invoice>
                </SalesInvoices>
              </SourceDocuments>
            </AuditFile>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val result = SaftPtSalesInvoiceParser.parse(xml)

        assertEquals("1.04_01", result.version)
        assertEquals(7, result.acceptedInvoices.size)
        assertEquals(1, result.excludedVoided)
        assertEquals(1, result.excludedReplaced)
        assertEquals(1, result.excludedSummary)
        assertEquals(0, result.excludedOtherStates)
        assertEquals(0, result.skippedInvalid)
        assertEquals(BigDecimal("20.00"), result.acceptedInvoices[0].netTotal)
        assertEquals("Hambúrguer", result.acceptedInvoices[0].lines.single().description)

        val credit = result.acceptedInvoices.first { it.invoiceType == "NC" }
        assertEquals(BigDecimal("-10.00"), credit.netTotal)
        assertEquals(BigDecimal("-1"), credit.lines.single().quantity)
        assertEquals(BigDecimal("-10.00"), credit.lines.single().netAmount)
        val returnTicket = result.acceptedInvoices.first { it.invoiceType == "TD" }
        assertEquals(BigDecimal("-5.00"), returnTicket.netTotal)
        assertEquals(BigDecimal("-1"), returnTicket.lines.single().quantity)
        assertTrue(result.fileSha256.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val xml = """<AuditFile xmlns="urn:OECD:StandardAuditFile-Tax:PT_1.04_01"><Header><AuditFileVersion>9.99</AuditFileVersion></Header><SourceDocuments><SalesInvoices/></SourceDocuments></AuditFile>"""
        val error = runCatching {
            SaftPtSalesInvoiceParser.parse(xml.toByteArray())
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("Versão SAF-T não suportada") == true)
    }

    @Test
    fun duplicateInvoiceIdentityDoesNotCreateTwoForecastRows() {
        val invoice = """
            <Invoice>
              <InvoiceNo>FT/1</InvoiceNo>
              <DocumentStatus><InvoiceStatus>N</InvoiceStatus></DocumentStatus>
              <InvoiceDate>2026-11-01</InvoiceDate><InvoiceType>FT</InvoiceType>
              <Line><Quantity>1</Quantity><UnitPrice>1</UnitPrice><Description>Café</Description><DebitAmount>1</DebitAmount></Line>
              <DocumentTotals><NetTotal>1</NetTotal><GrossTotal>1</GrossTotal></DocumentTotals>
            </Invoice>
        """.trimIndent()
        val xml = """<AuditFile xmlns="urn:OECD:StandardAuditFile-Tax:PT_1.04_01"><Header><AuditFileVersion>1.04_01</AuditFileVersion></Header><SourceDocuments><SalesInvoices>$invoice$invoice</SalesInvoices></SourceDocuments></AuditFile>"""
        val result = SaftPtSalesInvoiceParser.parse(xml.toByteArray())
        assertEquals(1, result.acceptedInvoices.size)
    }
}

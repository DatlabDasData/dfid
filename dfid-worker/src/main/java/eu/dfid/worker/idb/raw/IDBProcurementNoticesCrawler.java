package eu.dfid.worker.idb.raw;


import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.COUNTRY_COLUMN_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.DUE_DATE_COLUMN_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.NOTICE_TITLE_COLUMN_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.PROJECT_ID_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.PROJECT_NAME_COLUMN_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.PUBLICATION_DATE_COLUMN_METADATA_KEY;
import static eu.dfid.worker.idb.utils.IDBProcurementNoticesConstants.TYPE_COLUMN_METADATA_KEY;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

import eu.dfid.worker.raw.BaseDfidIncrementalPagedSourceHttpCrawler;
import eu.dl.core.UnrecoverableException;
import eu.dl.worker.Message;
import eu.dl.worker.MessageFactory;
import eu.dl.worker.raw.utils.CrawlerUtils;
import eu.dl.worker.utils.http.URLUtils;

/**
 * Procurement notices crawler for Inter-American Development Bank (IDB).
 *
 * @author Marek Mikes
 */
public final class IDBProcurementNoticesCrawler extends BaseDfidIncrementalPagedSourceHttpCrawler {
    private static final String VERSION = "2";

    private static final String RESULT_PAGE_URL_PATTERN_1 = "https://www.iadb.org/en/procurement-notices-search?query%5Bquery%5D=&quer" +
            "y%5BprojectCountry%5D=&query%5Bproject_number%5D=&query%5BdateFrom_plan%5D=";
    private static final String RESULT_PAGE_URL_PATTERN_2 = "&query%5BdateTo_plan%5D=";
    private static final String RESULT_PAGE_URL_PATTERN_3 = "&query%5BNoticesType%5D=&query%5BNotices%5D=";

    private static final String NEXT_BUTTON_XPATH = "//a[text()='Next ']";

    private static final LocalDate OLDEST_NOTICE_DATE = LocalDate.of(1999, Month.OCTOBER, 1);
    private static final DateTimeFormatter URL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM'%2F'dd'%2F'yyyy");

    /**
     * Initialization of the crawler.
     */
    public IDBProcurementNoticesCrawler() {
        super();
        getWebClient().getOptions().setJavaScriptEnabled(false);
    }

    @Override
    protected HtmlPage getSearchResultsStartPageForDate(final LocalDate incrementDate) {
        String dateString = incrementDate.format(URL_DATE_FORMATTER);
        String resultPageUrl = RESULT_PAGE_URL_PATTERN_1 + dateString + RESULT_PAGE_URL_PATTERN_2 + dateString + RESULT_PAGE_URL_PATTERN_3;
        try {
            return getWebClient().getPage(resultPageUrl);
        } catch (IOException e) {
            logger.error("Getting result for date {} failed", dateString, e);
            throw new UnrecoverableException("Unable to get result page", e);
        }
    }

    @Override
    public HtmlPage extractDetailsFromPage(final HtmlPage page) {
        @SuppressWarnings("unchecked") final List<HtmlTableRow> procurementNoticeRows = (List<HtmlTableRow>) page
                .getByXPath("//table/tbody/tr");

        for (HtmlTableRow procurementNoticeRow : procurementNoticeRows) {
            final HtmlAnchor documentLink = procurementNoticeRow.getFirstByXPath("td[3]/a");
            final String documentUrl = documentLink.getHrefAttribute();

            // add additional information
            final HashMap<String, Object> metaData = new HashMap<>();
            metaData.put(TYPE_COLUMN_METADATA_KEY, procurementNoticeRow.getCell(0).getTextContent().trim());
            metaData.put(COUNTRY_COLUMN_METADATA_KEY, procurementNoticeRow.getCell(1).getTextContent().trim());
            metaData.put(NOTICE_TITLE_COLUMN_METADATA_KEY, procurementNoticeRow.getCell(2).getTextContent().trim());

            String projectNameColumnText = procurementNoticeRow.getCell(3).getTextContent().trim();
            metaData.put(PROJECT_NAME_COLUMN_METADATA_KEY, projectNameColumnText);
            // get project ID from link if project name is filled. Link exists always but it has sense only when
            // project name is filled, otherwise the link opens page with no information
            // (e. g. http://www.iadb.org/en/projects/project-description-title,1303.html?id=CO1017)
            if (!projectNameColumnText.isEmpty()) {
                final HtmlAnchor projectLink = procurementNoticeRow.getCell(3).getFirstByXPath("a");
                metaData.put(PROJECT_ID_METADATA_KEY, URLUtils.getUrlParameter(projectLink.getHrefAttribute(), "id"));
            }

            metaData.put(PUBLICATION_DATE_COLUMN_METADATA_KEY, procurementNoticeRow.getCell(4).getTextContent().trim());
            metaData.put(DUE_DATE_COLUMN_METADATA_KEY, procurementNoticeRow.getCell(5).getTextContent().trim());

            final Message outgoingMessage = MessageFactory.getMessage();
            outgoingMessage.setValue("binaryDataUrl", documentUrl);
            outgoingMessage.setMetaData(metaData);
            publishMessage(outgoingMessage);
            logger.info("New message {} with url {} sent to be processed", outgoingMessage, documentUrl);
        }
        return page;
    }

    @Override
    public HtmlPage getNextPage(final HtmlPage actualPage) {
        return CrawlerUtils.clickElement(actualPage, NEXT_BUTTON_XPATH);
    }

    @Override
    protected ChronoUnit getIncrementUnit() {
        return ChronoUnit.DAYS;
    }

    @Override
    public boolean isPageValid(final HtmlPage page) {
        return true;
    }

    @Override
    protected String getVersion() {
        return VERSION;
    }

    @Override
    protected LocalDate getDefaultStartDate() {
        return OLDEST_NOTICE_DATE;
    }
}

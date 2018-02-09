package eu.dfid.worker.idb.raw;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import eu.dfid.worker.raw.BaseDfidIncrementalPagedSourceHttpCrawler;
import eu.dl.core.UnrecoverableException;
import eu.dl.worker.raw.utils.CrawlerUtils;

/**
 * Awarded contracts base crawler for Inter-American Development Bank (IDB).
 *
 * @author Marek Mikes
 */
abstract class BaseIDBAwardedContractsCrawler extends BaseDfidIncrementalPagedSourceHttpCrawler {
    private static final String VERSION = "3";
    private static final String RESULT_PAGE_URL_PATTERN = "http://www.iadb.org/en/projects/awarded-contracts,8181" +
            ".html?dateFrom=%1$s&dateTo=%1$s&typeSearch=%2$s";
    private static final String NEXT_BUTTON_XPATH = "//a[text()='Next ']";
    private static final DateTimeFormatter URL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM'%2F'dd'%2F'yyyy");

    /**
     * Initialization of the crawler.
     */
    BaseIDBAwardedContractsCrawler() {
        super();
        getWebClient().getOptions().setJavaScriptEnabled(false);
    }

    @Override
    protected final HtmlPage getSearchResultsStartPageForDate(final LocalDate incrementDate) {
        String dateString = incrementDate.format(URL_DATE_FORMATTER);
        String resultPageUrl = String.format(RESULT_PAGE_URL_PATTERN, dateString, getSearchTypeUrlValue());
        try {
            return getWebClient().getPage(resultPageUrl);
        } catch (IOException e) {
            logger.error("Getting result for date {} failed", dateString, e);
            throw new UnrecoverableException("Unable to get result page", e);
        }
    }

    @Override
    public final HtmlPage extractDetailsFromPage(final HtmlPage page) {
        // do not save empty page to DB
        if (page.getByXPath("//table[@id='searchResults']/tbody/tr").isEmpty()) {
            return page;
        }
        createAndPublishMessage(page.getUrl().toString(), page.getWebResponse().getContentAsString());
        return page;
    }

    @Override
    public final HtmlPage getNextPage(final HtmlPage actualPage) {
        return CrawlerUtils.clickElement(actualPage, NEXT_BUTTON_XPATH);
    }

    @Override
    public final boolean isPageValid(final HtmlPage page) {
        return true;
    }

    @Override
    protected final String getVersion() {
        return VERSION;
    }

    @Override
    protected ChronoUnit getIncrementUnit() {
        return ChronoUnit.DAYS;
    }

    /**
     * @return value of URL parameter represents search type (goods & works or consulting services)
     */
    protected abstract String getSearchTypeUrlValue();
}

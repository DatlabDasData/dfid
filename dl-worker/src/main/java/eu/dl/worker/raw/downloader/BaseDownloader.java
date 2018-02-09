package eu.dl.worker.raw.downloader;

import static eu.dl.worker.raw.utils.DownloaderUtils.generatePersistentId;

import java.util.List;

import eu.dl.core.UnrecoverableException;
import eu.dl.dataaccess.dao.RawDAO;
import eu.dl.dataaccess.dto.raw.Raw;
import eu.dl.worker.Message;
import eu.dl.worker.MessageFactory;
import eu.dl.worker.raw.BaseRawWorker;

/**
 * Provides basic functionality for data download from given url.
 * 
 * @param <T>
 *            item to be downloaded
 */
public abstract class BaseDownloader<T extends Raw> extends BaseRawWorker {

    private static final String INCOMING_EXCHANGE_NAME = "raw";

    protected RawDAO<T> rawDao;

    /**
     * Default constructor.
     */
    protected BaseDownloader() {
        super();        
        rawDao = getRawDataDao();
    }

    @Override
    public final void doWork(final Message message) {
        logger.debug("Doing work for message {}", message);

        // download and populate raw data (there might me more records at once => list of raw data objects)
        final List<T> rawData = downloadAndPopulateRawData(message);

        // save all the stuff
        for (T rawDataItem : rawData) {
            getTransactionUtils().begin();

            // generate persistent id if not already set by the worker logic
            if (rawDataItem.getPersistentId() == null) {
                rawDataItem.setPersistentId(generatePersistentId(rawDataItem, getSourceId()));
            }

            final String savedId = rawDao.save(rawDataItem);
            getTransactionUtils().commit();
            logger.info("Stored raw data as {}", savedId);
            // create and publish message with saved id
            final Message outgoingMessage = MessageFactory.getMessage();
            outgoingMessage.setValue("id", savedId);
            publishMessage(outgoingMessage);
        }
    }

    @Override
    public final void resend(final String version, final String dateFrom, final String dateTo) {
        logger.debug("Resending messages to be parsed.");

        try {
            String resendVersion = version;
            if (version.equals(LATEST)) {
                // current version data should be resent
                resendVersion = getVersion();
            }

            final List<T> rawDataItems = getRawDataDao().getMine(getName(), resendVersion, dateFrom, dateTo);

            for (final T rawDataItem : rawDataItems) {
                final Message outgoingMessage = MessageFactory.getMessage();
                outgoingMessage.setValue("id", rawDataItem.getId());
                publishMessage(outgoingMessage);
            }
        } catch (final Exception ex) {
            logger.error("Unable to resend messages for parsing {}", ex);
            throw new UnrecoverableException("Unable to resend messages for parsing", ex);
        }
    }

    @Override
    protected final String getIncomingExchangeName() {
        return INCOMING_EXCHANGE_NAME;
    }
    
    /**
     * Processes message from crawler, downloads requested source data and
     * prepares raw data object.
     *
     * @param message
     *            message from crawler containing parameters for downloading
     *
     * @return raw data objects populated with downloaded data
     */
    public abstract List<T> downloadAndPopulateRawData(Message message);

    /**
     * Returns specific DAO instance according to downloader type (tender, contracting authority etc.).
     *
     * @return dao instance for storing raw data to database
     */
    public abstract RawDAO<T> getRawDataDao();

    /**
     * Returns downloader version.
     *
     * @return downloader version
     */
    public abstract String getVersion();

    @Override
    protected final String getIncomingQueueName() {
        return getIncomingQueueNameFromConfig();
    }
}

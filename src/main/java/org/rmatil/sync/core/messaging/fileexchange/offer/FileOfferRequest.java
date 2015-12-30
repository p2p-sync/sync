package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.version.core.model.Sharer;
import org.rmatil.sync.version.core.model.Version;

import java.util.List;
import java.util.UUID;

/**
 * Send this request object to clients, to offer
 * a file creation / modification / deletion.
 */
public class FileOfferRequest implements IRequest {

    /**
     * The list of file versions which are present on this client for the particular file
     */
    protected List<Version> fileVersions;

    /**
     * The list of sharers which are present on this client for the file
     */
    protected List<Sharer> sharers;

    /**
     * The relative path of the file which has been
     * created / modified / deleted.
     */
    protected String relativeFilePath;

    /**
     * The offer type, i.e. Creation, Deletion, etc.
     */
    protected FileOfferType offerType;

    /**
     * The client device which sends this request
     */
    protected ClientDevice clientDevice;

    /**
     * The id of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * @param fileExchangeId   The id of the file exchange
     * @param clientDevice     The client device which sends this request
     * @param fileVersions     The list of known file versions
     * @param sharers          The list of known sharers
     * @param relativeFilePath The relative file path of the file to offer
     * @param offerType        The particular offer type, i.e. Creation, Deletion, etc.
     */
    public FileOfferRequest(UUID fileExchangeId, ClientDevice clientDevice, List<Version> fileVersions, List<Sharer> sharers, String relativeFilePath, FileOfferType offerType) {
        this.fileVersions = fileVersions;
        this.sharers = sharers;
        this.relativeFilePath = relativeFilePath;
        this.offerType = offerType;
        this.clientDevice = clientDevice;
        this.fileExchangeId = fileExchangeId;
    }

    /**
     * All known file versions
     *
     * @return The known file versions
     */
    public List<Version> getFileVersions() {
        return fileVersions;
    }

    /**
     * All known sharers of this file
     *
     * @return The known sharers
     */
    public List<Sharer> getSharers() {
        return sharers;
    }

    /**
     * The relative file path of the file
     *
     * @return The relative file path
     */
    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    /**
     * The type of this offering
     *
     * @return The offering type
     */
    public FileOfferType getOfferType() {
        return offerType;
    }

    @Override
    public UUID getExchangeId() {
        return fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}

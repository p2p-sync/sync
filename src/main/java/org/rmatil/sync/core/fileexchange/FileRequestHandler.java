package org.rmatil.sync.core.fileexchange;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.network.core.model.FileRequest;
import org.rmatil.sync.network.core.model.FileResponse;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.PathElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileRequestHandler implements ObjectDataReply {

    final static Logger logger = LoggerFactory.getLogger(FileRequestHandler.class);

    protected IStorageAdapter storageAdapter;

    public FileRequestHandler(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FileRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        FileRequest fileRequest = (FileRequest) request;
        IPathElement pathElement = new PathElement(fileRequest.getRelativeFilePath());

        // TODO: storage adapter: fetch only certain bytes from file using a stream
        // TODO: storage adapter: fetch meta information about a file (e.g. total file size)
        byte[] content = this.storageAdapter.read(pathElement);
        Data data = new Data(content, false);

        logger.info("Sending chunk 0 for file on path " + fileRequest.getRelativeFilePath());

        return new FileResponse(0, 1, content.length, data);
    }
}

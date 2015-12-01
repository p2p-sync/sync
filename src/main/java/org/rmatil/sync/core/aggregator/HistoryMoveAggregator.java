package org.rmatil.sync.core.aggregator;

import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.commons.hashing.HashingAlgorithm;
import org.rmatil.sync.event.aggregator.core.aggregator.MoveAggregator;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectManager;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HistoryMoveAggregator extends MoveAggregator {

    final static Logger logger = LoggerFactory.getLogger(HistoryMoveAggregator.class);

    protected IObjectManager objectManager;

    public HistoryMoveAggregator(IObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    /**
     * Aggregates events based on the hash of a certain
     * path element.
     * <p>
     * <i>Note</i>: Filenames are not considered, since it is possible,
     * that two files are renamed to the name of the other in the event list.
     * This would loose track of the correct renaming (i.e. moving) events.
     * Therefore, we only consider events which are applied to
     * files with the same hash
     *
     * @param events The events to aggregate
     *
     * @return The list of aggregated events
     */
    public List<IEvent> aggregate(List<IEvent> events) {
        Collections.sort(events);

        Map<String, List<IEvent>> sameHashEvents = new HashMap<String, List<IEvent>>();

        // add all events with the same file hash to the same place
        for (IEvent event : events) {
            // enrich delete event with last stored hash of history to force a move event
            // when an add event with the same hash occurs
            if (event instanceof DeleteEvent && null == event.getHash()) {
                try {
                    PathObject object = this.objectManager.getObject(Hash.hash(HashingAlgorithm.SHA_256, event.getPath().toString()));
                    Version lastVersion = object.getVersions().get(object.getVersions().size() - 1);

                    event = new DeleteEvent(
                            event.getPath(),
                            event.getName(),
                            lastVersion.getHash(),
                            event.getTimestamp()
                    );

                } catch (InputOutputException e) {
                    logger.error(e.getMessage());
                }
            }

            if (null == sameHashEvents.get(event.getHash())) {
                sameHashEvents.put(event.getHash(), new ArrayList<IEvent>());
            }

            List<IEvent> entries = sameHashEvents.get(event.getHash());
            entries.add(event);
        }

        // the final aggregated events which we will return
        List<IEvent> aggregatedEvents = new ArrayList<IEvent>();

        for (Map.Entry<String, List<IEvent>> entry : sameHashEvents.entrySet()) {
            if (entry.getValue().size() < 2) {
                // only one event for the same hash
                // -> no event aggregation
                aggregatedEvents.add(entry.getValue().get(0));
            } else {
                // add all events which we do not handle in this aggregator
                // These events should not occur in between the deletion & creation event
                aggregatedEvents.addAll(getInstances(entry.getValue(), ModifyEvent.class));
                aggregatedEvents.addAll(getInstances(entry.getValue(), MoveEvent.class));

                // -> delete & add => move
                List<IEvent> deleteHits = getInstances(entry.getValue(), DeleteEvent.class);
                List<IEvent> createHits = getInstances(entry.getValue(), CreateEvent.class);

                if (! deleteHits.isEmpty() && ! createHits.isEmpty()) {
                    // if the same file is multiple times deleted, we can not assign the
                    // correct create event to it
                    if (deleteHits.size() > 1) {
                        logger.info("Delete hits for file with the same hash is bigger than one. Skipping these events...");
                        aggregatedEvents.addAll(deleteHits);
                        aggregatedEvents.addAll(createHits); // add these too, otherwise they get lost
                        continue;
                    }

                    // if multiple files with the same hash are created, we can not
                    // assign the correct delete event to it
                    if (createHits.size() > 1) {
                        logger.info("Create hits for file with the same hash is bigger than one. Skipping these events...");
                        aggregatedEvents.addAll(createHits);
                        aggregatedEvents.addAll(deleteHits); // add these too, otherwise they get lost
                        continue;
                    }

                    IEvent deleteHit = deleteHits.get(0);
                    IEvent createHit = createHits.get(0);

                    // check timestamps: which was first?
                    if (deleteHit.getTimestamp() < createHit.getTimestamp()) {
                        MoveEvent moveEvent = new MoveEvent(deleteHit.getPath(), createHit.getPath(), createHit.getName(), createHit.getHash(), createHit.getTimestamp());
                        aggregatedEvents.add(moveEvent);
                        logger.trace("Creating moveEvent from " + deleteHit.getPath() + " to " + createHit.getPath());
                    } else {
                        // we just add both events unchanged to the results
                        aggregatedEvents.add(deleteHit);
                        aggregatedEvents.add(createHit);
                    }

                }
            }
        }

        Collections.sort(aggregatedEvents);

        return aggregatedEvents;
    }

}

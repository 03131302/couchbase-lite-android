package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ManagerTest extends LiteTestCase {

    public static final String TAG = "ManagerTest";

    public void testServer() throws CouchbaseLiteException {

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.cblite
        boolean mustExist = true;
        Database old = manager.getDatabaseWithoutOpening("foo", mustExist);
        if(old != null) {
            old.delete();
        }

        mustExist = false;
        Database db = manager.getDatabaseWithoutOpening("foo", mustExist);
        assertNotNull(db);
        assertEquals("foo", db.getName());
        assertTrue(db.getPath().startsWith(new LiteTestContext().getRootDirectory().getAbsolutePath()));
        assertFalse(db.exists());


        // because foo doesn't exist yet
        List<String> databaseNames = manager.getAllDatabaseNames();
        assertTrue(!databaseNames.contains("foo"));

        assertTrue(db.open());
        assertTrue(db.exists());

        databaseNames = manager.getAllDatabaseNames();
        assertTrue(databaseNames.contains("foo"));

        db.close();
        db.delete();

    }


    public void testUpgradeOldDatabaseFiles() throws Exception {

        String directoryName = "test-directory-" + System.currentTimeMillis();
        LiteTestContext context = new LiteTestContext(directoryName);

        File directory = context.getFilesDir();
        if(!directory.exists()) {
            boolean result = directory.mkdir();
            if(!result) {
                throw new IOException("Unable to create directory " + directory);
            }
        }
        File oldTouchDbFile = new File(directory, String.format("old%s", Manager.DATABASE_SUFFIX_OLD));
        oldTouchDbFile.createNewFile();
        File newCbLiteFile = new File(directory, String.format("new%s", Manager.DATABASE_SUFFIX));
        newCbLiteFile.createNewFile();

        File migratedOldFile = new File(directory, String.format("old%s", Manager.DATABASE_SUFFIX));
        migratedOldFile.createNewFile();
        super.stopCBLite();
        manager = new Manager(context, Manager.DEFAULT_OPTIONS);

        assertTrue(migratedOldFile.exists());
        //cannot rename old.touchdb to old.cblite, because old.cblite already exists
        assertTrue(oldTouchDbFile.exists());
        assertTrue(newCbLiteFile.exists());

        assertEquals(3, directory.listFiles().length);

        super.stopCBLite();
        migratedOldFile.delete();
        manager = new Manager(context, Manager.DEFAULT_OPTIONS);

        //rename old.touchdb in old.cblite, previous old.cblite already doesn't exist
        assertTrue(migratedOldFile.exists());
        assertTrue(oldTouchDbFile.exists() == false);
        assertTrue(newCbLiteFile.exists());
        assertEquals(2, directory.listFiles().length);

    }

    public void testReplaceDatabaseNamedNoAttachments() throws CouchbaseLiteException {

        //Copy database from assets to local storage
        InputStream dbStream = getAsset("noattachments.cblite");

        manager.replaceDatabase("replaced", dbStream, null);

        //Now validate the number of files in the DB
        assertEquals(10,manager.getDatabase("replaced").getDocumentCount());

    }

    public void testReplaceDatabaseNamedWithAttachments() throws CouchbaseLiteException {

        InputStream dbStream = getAsset("withattachments.cblite");

        String[] attachmentlist = null;

        Map<String, InputStream> attachments = new HashMap<String, InputStream>();
        InputStream blobStream = getAsset("attachments/356a192b7913b04c54574d18c28d46e6395428ab.blob");
        attachments.put("356a192b7913b04c54574d18c28d46e6395428ab.blob",blobStream);

        manager.replaceDatabase("replaced2", dbStream, attachments);

        //Validate the number of files in the DB
        assertEquals(1,manager.getDatabase("replaced2").getDocumentCount());

        //get the attachment from the document
        Document doc = manager.getDatabase("replaced2").getExistingDocument("168e0c56-4588-4df4-8700-4d5115fa9c74");

        assertNotNull(doc);

        RevisionInternal gotRev1 = database.getDocumentWithIDAndRev(doc.getId(), doc.getCurrentRevisionId(), EnumSet.noneOf(Database.TDContentOptions.class));

    }

    public void testGetDatabaseConcurrently() throws Exception {
        final String DATABASE_NAME = "test";
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> callables = new ArrayList<Callable<Void>>(2);
            for (int i = 0; i < 2; i++) {
                callables.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        manager.getDatabase(DATABASE_NAME);
                        return null;
                    }
                });
            }

            List<Future<Void>> results = executorService.invokeAll(callables);
            for (Future<Void> future : results) {
                // Will throw an exception, thus failing the test, if anything went wrong.
                future.get();
            }
        } finally {
            // Cleanup
            Database a = manager.getExistingDatabase(DATABASE_NAME);
            if (a != null) {
                a.delete();
            }
            executorService.shutdown();
        }
    }


    /**
     * Error after close DB client
     * https://github.com/couchbase/couchbase-lite-java/issues/52
     */
    public void testClose() throws Exception {
        Log.d(Log.TAG, "START testClose()");

        boolean success = false;

        // create mock server
        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        server.play();

        // checkpoint PUT or GET response (sticky) (for both push and pull)
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // create pull replication & start it
        Replication pull = database.createPullReplication(server.getUrl("/db"));
        pull.setContinuous(true);
        final CountDownLatch pullIdleState = new CountDownLatch(1);
        ReplicationIdleObserver pullIdleObserver = new ReplicationIdleObserver(pullIdleState);
        pull.addChangeListener(pullIdleObserver);
        pull.start();

        // create push replication & start it
        Replication push = database.createPullReplication(server.getUrl("/db"));
        push.setContinuous(true);
        final CountDownLatch pushIdleState = new CountDownLatch(1);
        ReplicationIdleObserver pushIdleObserver = new ReplicationIdleObserver(pushIdleState);
        push.addChangeListener(pushIdleObserver);
        push.start();

        // wait till both push and pull replicators become idle.
        success = pullIdleState.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        pull.removeChangeListener(pullIdleObserver);
        success = pushIdleState.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        push.removeChangeListener(pushIdleObserver);

        final CountDownLatch pullStoppedState = new CountDownLatch(1);
        ReplicationFinishedObserver pullStoppedObserver = new ReplicationFinishedObserver(pullStoppedState);
        pull.addChangeListener(pullStoppedObserver);
        final CountDownLatch pushStoppedState = new CountDownLatch(1);
        ReplicationFinishedObserver pushStoppedObserver = new ReplicationFinishedObserver(pushStoppedState);
        push.addChangeListener(pushStoppedObserver);

        // close Manager, which close database(s) and replicator(s)
        manager.close();

        // not need to wait. manager.close() should wait till replicators are closed.
        assertEquals(0, pullStoppedState.getCount());
        assertEquals(0, pushStoppedState.getCount());
        pull.removeChangeListener(pullStoppedObserver);
        push.removeChangeListener(pushStoppedObserver);

        // all threads for Executors should be terminated.
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            assertEquals(-1, t.getName().indexOf("CBLManagerWorkExecutor"));
            assertEquals(-1, t.getName().indexOf("CBLRequestWorker"));
        }

        // shutdown mock server
        server.shutdown();

        Log.d(Log.TAG, "END testClose()");
    }
}

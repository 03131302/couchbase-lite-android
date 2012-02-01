package com.couchbase.touchdb.router;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDDatabase.TDContentOptions;
import com.couchbase.touchdb.TDQueryOptions;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.TouchDBVersion;
import com.couchbase.touchdb.replicator.TDPuller;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.TDReplicator;


public class TDRouter {
    private TDServer server;
    private TDDatabase db;
    private TDURLConnection connection;
    private Map<String,String> queries;

    public static String getVersionString() {
        return TouchDBVersion.TouchDBVersionNumber;
    }

    public TDRouter(TDServer server, TDURLConnection connection) {
        this.server = server;
        this.connection = connection;
    }

    public Map<String,String> getQueries() {
        if(queries == null) {
            String queryString = connection.getURL().getQuery();
            if(queryString != null && queryString.length() > 0) {
                queries = new HashMap<String,String>();
                for (String component : queryString.split("&")) {
                    int location = component.indexOf('=');
                    if(location > 0) {
                        String key = component.substring(0, location);
                        String value = component.substring(location + 1);
                        queries.put(key, value);
                    }
                }

            }
        }
        return queries;
    }

    public String getQuery(String param) {
        Map<String,String> queries = getQueries();
        if(queries != null) {
            String value = queries.get(param);
            return URLDecoder.decode(value);
        }
        return null;
    }

    public boolean getBooleanQuery(String param) {
        String value = getQuery(param);
        return (value != null) && !"false".equals(value) && !"0".equals(value);
    }

    public int getIntQuery(String param, int defaultValue) {
        int result = defaultValue;
        String value = getQuery(param);
        if(value != null) {
            try {
                result = Integer.parseInt(param);
            } catch (NumberFormatException e) {
                //ignore, will return default value
            }
        }

        return result;
    }

    public Object getJSONQuery(String param) throws JsonMappingException, JsonParseException, IOException {
        String value = getQuery(param);
        if(value == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        Object result = mapper.readValue(value, Object.class);
        return result;
    }

    public boolean cacheWithEtag(String etag) {
        String eTag = String.format("\"%s\"", etag);
        connection.getResHeader().add("Etag", eTag);
        return eTag.equals(connection.getRequestProperty("If-None-Match"));
    }

    public Map<String,Object> getBodyAsDictionary() {
        try {
            byte[] bodyBytes = ((ByteArrayOutputStream)connection.getOutputStream()).toByteArray();
            ObjectMapper mapper = new ObjectMapper();
            Map<String,Object> bodyMap = mapper.readValue(bodyBytes, Map.class);
            return bodyMap;
        } catch (IOException e) {
            return null;
        }
    }

    public EnumSet<TDContentOptions> getContentOptions() {
        EnumSet<TDContentOptions> result = EnumSet.noneOf(TDContentOptions.class);
        if(getBooleanQuery("attachments")) {
            result.add(TDContentOptions.TDIncludeAttachments);
        }
        if(getBooleanQuery("local_seq")) {
            result.add(TDContentOptions.TDIncludeLocalSeq);
        }
        if(getBooleanQuery("conflicts")) {
            result.add(TDContentOptions.TDIncludeConflicts);
        }
        if(getBooleanQuery("revs")) {
            result.add(TDContentOptions.TDIncludeRevs);
        }
        if(getBooleanQuery("revs_info")) {
            result.add(TDContentOptions.TDIncludeRevsInfo);
        }
        return result;
    }

    public boolean getQueryOptions(TDQueryOptions options) throws JsonMappingException, JsonParseException, IOException {
        // http://wiki.apache.org/couchdb/HTTP_view_API#Querying_Options
        options.setSkip(getIntQuery("skip", options.getSkip()));
        options.setLimit(getIntQuery("limit", options.getLimit()));
        options.setGroupLevel(getIntQuery("group_level", options.getGroupLevel()));
        options.setDescending(getBooleanQuery("descending"));
        options.setIncludeDocs(getBooleanQuery("include_docs"));
        options.setUpdateSeq(getBooleanQuery("update_seq"));
        if(getQuery("inclusive_end") != null) {
            options.setInclusiveEnd(getBooleanQuery("inclusive_end"));
        }
        options.setReduce(getBooleanQuery("reduce"));
        options.setGroup(getBooleanQuery("group"));
        options.setContentOptions(getContentOptions());
        options.setStartKey(getJSONQuery("startkey"));
        options.setEndKey(getJSONQuery("endkey"));
        Object key = getJSONQuery("key");
        if(key != null) {
            List<Object> keys = new ArrayList<Object>();
            keys.add(key);
            options.setKeys(keys);
        }
        return true;
    }

    public String getMultipartRequestType() {
        String accept = connection.getRequestProperty("Accept");
        if(accept.startsWith("multipart/")) {
            return accept;
        }
        return null;
    }

    public TDStatus openDB() {
        if(db == null) {
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }
        if(!db.exists()) {
            return new TDStatus(TDStatus.NOT_FOUND);
        }
        if(!db.open()) {
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }
        return new TDStatus(TDStatus.OK);
    }

    public static List<String> splitPath(URL url) {
        String pathString = url.getPath();
        if(pathString.startsWith("/")) {
            pathString = pathString.substring(1);
        }
        List<String> result = new ArrayList<String>();
        for (String component : pathString.split("/")) {
            result.add(URLDecoder.decode(component));
        }
        return result;
    }

    public void start() {
        // Refer to: http://wiki.apache.org/couchdb/Complete_HTTP_API_Reference

        // We're going to map the request into a method call using reflection based on the method and path.
        // Accumulate the method name into the string 'message':
        String method = connection.getRequestMethod();
        if("HEAD".equals(method)) {
            method = "GET";
        }
        String message = String.format("do_%s", method);

        // First interpret the components of the request:
        List<String> path = splitPath(connection.getURL());
        if(path == null) {
            connection.setResponseCode(TDStatus.BAD_REQUEST);
            return;
        }

        int pathLen = path.size();
        if(pathLen > 0) {
            String dbName = path.get(0);
            if(dbName.startsWith("_")) {
                message += dbName;  // special root path, like /_all_dbs
            } else {
                db = server.getDatabaseNamed(dbName);
                if(db == null) {
                    connection.setResponseCode(TDStatus.BAD_REQUEST);
                    return;
                }
            }
        } else {
            message += "Root";
        }

        String docID = null;
        if(db != null && pathLen > 1) {
            // Make sure database exists, then interpret doc name:
            TDStatus status = openDB();
            if(!status.isSuccessful()) {
                connection.setResponseCode(status.getCode());
                return;
            }
            String name = path.get(1);
            if(!name.startsWith("_")) {
                // Regular document
                if(!TDDatabase.isValidDocumentId(name)) {
                    connection.setResponseCode(TDStatus.BAD_REQUEST);
                    return;
                }
                docID = name;
            } else if("_design".equals(name) || "_local".equals(name)) {
                // "_design/____" and "_local/____" are document names
                if(pathLen <= 2) {
                    connection.setResponseCode(TDStatus.NOT_FOUND);
                    return;
                }
                docID = name + "/" + path.get(2);
                path.set(1, docID);
                path.remove(2);
                pathLen--;
            } else if(name.startsWith("_design") || name.startsWith("_local")) {
                // This is also a document, just with a URL-encoded "/"
                docID = name;
            } else {
                // Special document name like "_all_docs":
                message += name;
                if(pathLen > 2) {
                    List<String> subList = path.subList(2, pathLen-1);
                    StringBuilder sb = new StringBuilder();
                    Iterator<String> iter = subList.iterator();
                    while(iter.hasNext()) {
                        sb.append(iter.next());
                        if(iter.hasNext()) {
                            sb.append("/");
                        }
                    }
                    docID = sb.toString();
                }
            }
        }

        String attachmentName = null;
        if(docID != null && pathLen > 2) {
            // Interpret attachment name:
            attachmentName = path.get(2);
            if(attachmentName.startsWith("_") && docID.startsWith("_design")) {
                // Design-doc attribute like _info or _view
                docID = docID.substring(8); // strip the "_design/" prefix
                attachmentName = pathLen > 3 ? path.get(3) : null;
            }
        }

        // Send myself a message based on the components:
        TDStatus status = new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        try {
            Method m = this.getClass().getMethod(message, TDDatabase.class, String.class, String.class);
            status = (TDStatus)m.invoke(this, db, docID, attachmentName);
        } catch (NoSuchMethodException msme) {
            try {
                Method m = this.getClass().getMethod("do_UNKNOWN", TDDatabase.class, String.class, String.class);
                status = (TDStatus)m.invoke(this, db, docID, attachmentName);
            } catch (Exception e) {
                connection.setResponseCode(TDStatus.INTERNAL_SERVER_ERROR);
                return;
            }
        } catch (Exception e) {
            connection.setResponseCode(TDStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        // Configure response headers:
        if(status.isSuccessful() && connection.getResponseBody() == null && connection.getHeaderField("Content-Type") == null) {
            connection.setResponseBody(new TDBody("{\"ok\":true}".getBytes()));
        }

        if(connection.getResponseBody() != null && connection.getResponseBody() == null && connection.getResponseBody().isValidJSON()) {
            connection.getResHeader().add("Content-Type", "application/json");
        }

        // Check for a mismatch between the Accept request header and the response type:
        String accept = connection.getRequestProperty("Accept");
        if(accept != null && !"*/*".equals(accept)) {
            String responseType = connection.getBaseContentType();
            if(responseType != null && accept.indexOf(responseType) < 0) {
                Log.e(TDDatabase.TAG, String.format("Error 406: Can't satisfy request Accept: %s", accept));
                status = new TDStatus(TDStatus.NOT_ACCEPTABLE);
            }
        }

        connection.getResHeader().add("Server", String.format("TouchDB %s", getVersionString()));

        //since we did everything in this thread we know the response is ready
        connection.setResponseCode(status.getCode());
    }

    public TDStatus do_UNKNWON(TDDatabase db, String docID, String attachmentName) {
        return new TDStatus(TDStatus.BAD_REQUEST);
    }

    /*************************************************************************************************/
    /*** TDRouter+Handlers                                                                         ***/
    /*************************************************************************************************/

    public void setResponseLocation(URL url) {
        String location = url.toExternalForm();
        String query = url.getQuery();
        if(query != null) {
            int startOfQuery = location.indexOf(query);
            if(startOfQuery > 0) {
                location = location.substring(0, startOfQuery);
            }
        }
        connection.getResHeader().add("Location", location);
    }

    /** SERVER REQUESTS: **/

    public TDStatus do_GETRoot(TDDatabase _db, String _docID, String _attachmentName) {
        Map<String,Object> info = new HashMap<String,Object>();
        info.put("TouchDB", "Welcome");
        info.put("couchdb", "Welcome"); // for compatibility
        info.put("version", getVersionString());
        connection.setResponseBody(new TDBody(info));
        return new TDStatus(TDStatus.OK);
    }

    public TDStatus do_GET_all_dbs(TDDatabase _db, String _docID, String _attachmentName) {
        List<String> dbs = server.allDatabaseNames();
        connection.setResponseBody(new TDBody(dbs));
        return new TDStatus(TDStatus.OK);
    }

    public TDStatus do_POST_replicate(TDDatabase _db, String _docID, String _attachmentName) {
        // Extract the parameters from the JSON request body:
        // http://wiki.apache.org/couchdb/Replication
        Map<String,Object> body = getBodyAsDictionary();
        if(body == null) {
            return new TDStatus(TDStatus.BAD_REQUEST);
        }
        String source = (String)body.get("source");
        String target = (String)body.get("target");
        Boolean createTargetBoolean = (Boolean)body.get("create_target");
        boolean createTarget = (createTargetBoolean != null && createTargetBoolean.booleanValue());
        Boolean continuousBoolean = (Boolean)body.get("continuous");
        boolean continuous = (continuousBoolean != null && continuousBoolean.booleanValue());
        Boolean cancelBoolean = (Boolean)body.get("cancel");
        boolean cancel = (cancelBoolean != null && cancelBoolean.booleanValue());

        // Map the 'source' and 'target' JSON params to a local database and remote URL:
        if(source == null || target == null) {
            return new TDStatus(TDStatus.BAD_REQUEST);
        }
        boolean push = false;
        TDDatabase db = server.getExistingDatabaseNamed(source);
        String remoteStr = null;
        if(db != null) {
            remoteStr = target;
            push = true;
        } else {
            remoteStr = source;
            if(createTarget && !cancel) {
                db = server.getDatabaseNamed(target);
                if(!db.open()) {
                    return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                db = server.getExistingDatabaseNamed(target);
            }
            if(db == null) {
                return new TDStatus(TDStatus.NOT_FOUND);
            }
        }

        URL remote = null;
        try {
            remote = new URL(remoteStr);
        } catch (MalformedURLException e) {
            return new TDStatus(TDStatus.BAD_REQUEST);
        }
        if(remote == null || !remote.getProtocol().equals("http")) {
            return new TDStatus(TDStatus.BAD_REQUEST);
        }

        if(!cancel) {
            // Start replication:
            TDReplicator repl = db.getReplicator(remote, push, continuous);
            if(repl == null) {
                return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
            }
            if(push) {
                ((TDPusher)repl).setCreateTarget(createTarget);
            } else {
                TDPuller pullRepl = (TDPuller)repl;
                String filterName = (String)body.get("filter");
                if(filterName != null) {
                    pullRepl.setFilterName(filterName);
                    Map<String,Object> filterParams = (Map<String,Object>)body.get("query_params");
                    if(filterParams != null) {
                        pullRepl.setFilterParams(filterParams);
                    }
                }
            }
            repl.start();
            Map<String,Object> result = new HashMap<String,Object>();
            result.put("session_id", repl.getSessionID());
            connection.setResponseBody(new TDBody(result));
        } else {
            // Cancel replication:
            TDReplicator repl = db.getActiveReplicator(remote, push);
            if(repl == null) {
                return new TDStatus(TDStatus.NOT_FOUND);
            }
            repl.stop();
        }
        return new TDStatus(TDStatus.OK);
    }

    public TDStatus do_GET_uuids(TDDatabase _db, String _docID, String _attachmentName) {
        int count = Math.min(1000, getIntQuery("count", 1));
        List<String> uuids = new ArrayList<String>(count);
        for(int i=0; i<count; i++) {
            uuids.add(TDDatabase.generateDocumentId());
        }
        Map<String,Object> result = new HashMap<String,Object>();
        result.put("uuids", uuids);
        connection.setResponseBody(new TDBody(result));
        return new TDStatus(TDStatus.OK);
    }

    public TDStatus do_GET_active_tasks(TDDatabase _db, String _docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HttpGetActiveTasks
        List<Map<String,Object>> activities = new ArrayList<Map<String,Object>>();
        for (TDDatabase db : server.allOpenDatabases()) {
            for (TDReplicator replicator : db.getActiveReplicators()) {
                String source = replicator.getRemote().toExternalForm();
                String target = db.getName();
                if(replicator.isPush()) {
                    String tmp = source;
                    source = target;
                    target = tmp;
                }
                int processed = replicator.getChangesProcessed();
                int total = replicator.getChangesTotal();
                String status = String.format("Processed %d / %d changes", processed, total);
                int progress = (total > 0) ? Math.round(100 * processed / (float)total) : 0;
                Map<String,Object> activity = new HashMap<String,Object>();
                activity.put("type", "Replication");
                activity.put("task", replicator.getSessionID());
                activity.put("source", source);
                activity.put("target", target);
                activity.put("status", status);
                activity.put("progress", progress);
                activities.add(activity);
            }
        }
        connection.setResponseBody(new TDBody(activities));
        return new TDStatus(TDStatus.OK);
    }

    /** DATABASE REQUESTS: **/

    public TDStatus do_GET(TDDatabase _db, String _docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HTTP_database_API#Database_Information
        TDStatus status = openDB();
        if(!status.isSuccessful()) {
            return status;
        }
        int num_docs = db.getDocumentCount();
        long update_seq = db.getLastSequence();
        Map<String, Object> result = new HashMap<String,Object>();
        result.put("db_name", db.getName());
        result.put("db_uuid", db.publicUUID());
        result.put("doc_count", num_docs);
        result.put("update_seq", update_seq);
        result.put("disk_size", db.totalDataSize());
        connection.setResponseBody(new TDBody(result));
        return new TDStatus(TDStatus.OK);
    }

    public TDStatus do_PUT(TDDatabase _db, String _docID, String _attachmentName) {
        if(db.exists()) {
            return new TDStatus(TDStatus.PRECONDITION_FAILED);
        }
        if(!db.open()) {
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }
        setResponseLocation(connection.getURL());
        return new TDStatus(TDStatus.CREATED);
    }

}

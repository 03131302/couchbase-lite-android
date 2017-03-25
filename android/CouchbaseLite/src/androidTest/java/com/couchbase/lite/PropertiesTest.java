package com.couchbase.lite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertiesTest extends BaseTest {
    @Before
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void testPropertiesNullValue() {
        Document doc = db.getDocument("doc1");
        doc.set("key", null);

        // before save
        assertEquals(null, doc.getString("key"));
        assertEquals(false, doc.getBoolean("key"));
        assertEquals(0.0F, doc.getFloat("key"), 0.0F);
        assertEquals(0.0, doc.getDouble("key"), 0.0);
        assertEquals(0, doc.getInt("key"));
        assertEquals(null, doc.getArray("key"));
        assertEquals(null, doc.getDate("key"));
        assertEquals(null, doc.get("key"));

        // after save
        doc.save();
        assertEquals(null, doc.getString("key"));
        assertEquals(false, doc.getBoolean("key"));
        assertEquals(0.0F, doc.getFloat("key"), 0.0F);
        assertEquals(0.0, doc.getDouble("key"), 0.0);
        assertEquals(0, doc.getInt("key"));
        assertEquals(null, doc.getArray("key"));
        assertEquals(null, doc.getDate("key"));
        assertEquals(null, doc.get("key"));

        // re-obtain
        doc = db.getDocument("doc1");
        assertEquals(null, doc.getString("key"));
        assertEquals(false, doc.getBoolean("key"));
        assertEquals(0.0F, doc.getFloat("key"), 0.0F);
        assertEquals(0.0, doc.getDouble("key"), 0.0);
        assertEquals(0, doc.getInt("key"));
        assertEquals(null, doc.getArray("key"));
        assertEquals(null, doc.getDate("key"));
        assertEquals(null, doc.get("key"));

        // after reopen
        reopenDB();
        doc = db.getDocument("doc1");
        assertEquals(null, doc.getString("key"));
        assertEquals(false, doc.getBoolean("key"));
        assertEquals(0.0F, doc.getFloat("key"), 0.0F);
        assertEquals(0.0, doc.getDouble("key"), 0.0);
        assertEquals(0, doc.getInt("key"));
        assertEquals(null, doc.getArray("key"));
        assertEquals(null, doc.getDate("key"));
        assertEquals(null, doc.get("key"));
    }

    @Test
    public void testPropertiesDifferentType() {
        Document doc = db.getDocument("doc1");
        doc.set("string", "demo");
        doc.set("int", 1);

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(0, doc.getInt("string"));
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // after save
        doc.save();

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // re-obtain
        doc = db.getDocument("doc1");

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // after reopen
        reopenDB();

        doc = db.getDocument("doc1");

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));
    }
}

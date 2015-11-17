/*
 * Copyright (c) 2015 Eike Stepper (Berlin, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.userstorage.tests;

import static org.hamcrest.MatcherAssert.assertThat;

import org.eclipse.userstorage.IBlob;
import org.eclipse.userstorage.IStorage;
import org.eclipse.userstorage.IStorageService;
import org.eclipse.userstorage.StorageFactory;
import org.eclipse.userstorage.internal.Activator;
import org.eclipse.userstorage.internal.Session;
import org.eclipse.userstorage.internal.util.IOUtil;
import org.eclipse.userstorage.internal.util.StringUtil;
import org.eclipse.userstorage.spi.ISettings;
import org.eclipse.userstorage.tests.util.FixedCredentialsProvider;
import org.eclipse.userstorage.tests.util.USSServer;
import org.eclipse.userstorage.tests.util.USSServer.NOOPLogger;
import org.eclipse.userstorage.tests.util.USSServer.User;
import org.eclipse.userstorage.util.BadApplicationTokenException;
import org.eclipse.userstorage.util.BadKeyException;
import org.eclipse.userstorage.util.ConflictException;
import org.eclipse.userstorage.util.FileStorageCache;
import org.eclipse.userstorage.util.ProtocolException;
import org.eclipse.userstorage.util.Settings;
import org.eclipse.userstorage.util.Settings.MemorySettings;

import org.eclipse.jetty.util.log.Log;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * @author Eike Stepper
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class StorageTests extends AbstractTest
{
  private static final boolean REMOTE = Boolean.getBoolean(StorageTests.class.getName() + ".remote");

  private static final File SERVER = new File(System.getProperty("java.io.tmpdir"), "uss-tests/server");

  private static final File CACHE = new File(System.getProperty("java.io.tmpdir"), "uss-tests/cache");

  private static final String APPLICATION_TOKEN = "pDKTqBfDuNxlAKydhEwxBZPxa4q";

  private static final String KEY = "test_blob";

  private USSServer server;

  private User user;

  private IStorageService.Dynamic service;

  private StorageFactory factory;

  private TestCache cache;

  @BeforeClass
  public static void disableLogging()
  {
    Log.setLog(new NOOPLogger());
  }

  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    Activator.start();

    ISettings settings;

    if (REMOTE)
    {
      settings = Settings.NONE;
    }
    else
    {
      IOUtil.deleteFiles(SERVER);

      server = new USSServer(8080, SERVER);
      user = server.addUser(FixedCredentialsProvider.DEFAULT_CREDENTIALS);
      server.getApplicationTokens().add(APPLICATION_TOKEN);
      int port = server.start();

      final String serviceURI = "http://localhost:" + port;
      service = IStorageService.Registry.INSTANCE.addService("Test Service", StringUtil.newURI(serviceURI));

      settings = new MemorySettings(Collections.singletonMap(APPLICATION_TOKEN, serviceURI));
    }

    factory = new StorageFactory(settings);

    IOUtil.deleteFiles(CACHE);
    cache = new TestCache(CACHE);
  }

  @Override
  public void tearDown() throws Exception
  {
    factory = null;
    cache = null;

    if (hasLocalServer())
    {
      service.remove();
      service = null;

      server.stop();
      server = null;

      user = null;
    }

    Activator.stop();
    super.tearDown();
  }

  @Test
  public void testApplicationToken() throws Exception
  {
    try
    {
      factory.create(null);
      fail("BadApplicationTokenException expected");
    }
    catch (BadApplicationTokenException expected)
    {
      // SUCCESS
    }

    try
    {
      factory.create("aaaa"); // Too short.
      fail("BadApplicationTokenException expected");
    }
    catch (BadApplicationTokenException expected)
    {
      // SUCCESS
    }

    try
    {
      factory.create("aaaaaaaaaaaaaaaaaaaaaaaaaaaa"); // Too long.
      fail("BadApplicationTokenException expected");
    }
    catch (BadApplicationTokenException expected)
    {
      // SUCCESS
    }

    try
    {
      factory.create("1aaaaaaaaa"); // Too long.
      fail("BadApplicationTokenException expected");
    }
    catch (BadApplicationTokenException expected)
    {
      // SUCCESS
    }

    factory.create("aaaaa"); // Just short enough.
    factory.create("aaaaaaaaaaaaaaaaaaaaaaaaaaa"); // Just long enough.
  }

  @Test
  public void testKey() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);

    try
    {
      storage.getBlob(null);
      fail("BadKeyException expected");
    }
    catch (BadKeyException expected)
    {
      // SUCCESS
    }

    try
    {
      storage.getBlob("aaaa"); // Too short.
      fail("BadKeyException expected");
    }
    catch (BadKeyException expected)
    {
      // SUCCESS
    }

    try
    {
      storage.getBlob("aaaaaaaaaaaaaaaaaaaaaaaaaaa"); // Too long.
      fail("BadKeyException expected");
    }
    catch (BadKeyException expected)
    {
      // SUCCESS
    }

    try
    {
      storage.getBlob("1aaaaaaaaa"); // Too long.
      fail("BadKeyException expected");
    }
    catch (BadKeyException expected)
    {
      // SUCCESS
    }

    storage.getBlob("aaaaa"); // Just short enough.
    storage.getBlob("aaaaaaaaaaaaaaaaaaaaaaaaa"); // Just long enough.
  }

  @Test
  public void testUserAgent() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    System.setProperty(Session.USER_AGENT_PROPERTY, "malicious/client");

    try
    {
      IBlob blob = storage.getBlob("any_blob");
      blob.getContents();
      fail("ProtocolException expected");
    }
    catch (ProtocolException expected)
    {
      assertThat(expected.getStatusCode(), is(403)); // Forbidden.
    }
    finally
    {
      System.clearProperty(Session.USER_AGENT_PROPERTY);
    }
  }

  @Test
  public void testUpdate() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value = "A short UTF-8 string value";
    assertThat(blob.setContentsUTF(value), is(true));

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value));
    assertThat(blobInfo.eTag, is(blob.getETag()));
  }

  @Test
  public void testUpdateWithCache() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN, cache);
    IBlob blob = storage.getBlob(makeKey());

    String value = "A short UTF-8 string value";
    assertThat(blob.setContentsUTF(value), is(true));
    assertThat(readCache(blob.getKey(), null), is(value));
    assertThat(readCache(blob.getKey(), ".properties"), containsString("etag=" + blob.getETag()));
  }

  @Test
  public void testUpdateMulti() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    blob.setContentsUTF("Text 1");
    blob.setContentsUTF("Text 2");
    blob.setContentsUTF("Text 3");
  }

  @Test
  public void testRetrieveNotFound() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob("aaaaaaaaaa");
    assertThat(blob.getContents(), isNull());
  }

  @Test
  public void testRetrieve() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value = "A short UTF-8 string value";
    blob.setContentsUTF(value);

    assertThat(blob.getContentsUTF(), is(value));

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value));
    assertThat(blobInfo.eTag, is(blob.getETag()));
  }

  @Test
  public void testRetrieveWithCache() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN, cache);
    IBlob blob = storage.getBlob(makeKey());

    String value = "A short UTF-8 string value";
    blob.setContentsUTF(value);

    assertThat(blob.getContentsUTF(), is(value));
    assertThat(readCache(blob.getKey(), null), is(value));
    assertThat(readCache(blob.getKey(), ".properties"), containsString("etag=" + blob.getETag()));
  }

  @Test
  public void testRetrieveMulti() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());
    blob.setContentsUTF("A short UTF-8 string value");

    blob.getContents();
    blob.getContents();
    blob.getContents();
    blob.getContents();
    blob.getContents();
  }

  @Test
  public void testConflict() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);
    String eTag1 = blob.getETag();

    // Prepare the conflict.
    String value2 = "Different content";
    String eTag2 = writeServer(blob, value2);

    String value3 = "And now a conflicting string";

    try
    {
      blob.setContentsUTF(value3);
      fail("ConflictException expected");
    }
    catch (ConflictException expected)
    {
      assertThat(expected.getStatusCode(), is(409)); // Conflict.
      assertThat(expected.getETag(), isNull());
    }

    assertThat(blob.getETag(), is(eTag1));

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value2));
    assertThat(blobInfo.eTag, is(eTag2));
  }

  @Test
  public void testConflictWithCache() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN, cache);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);
    String eTag1 = blob.getETag();

    // Prepare the conflict.
    writeServer(blob, "Different content");

    String value3 = "And now a conflicting string";

    try
    {
      blob.setContentsUTF(value3);
      fail("ConflictException expected");
    }
    catch (ConflictException expected)
    {
      assertThat(expected.getStatusCode(), is(409)); // Conflict.
      assertThat(expected.getETag(), isNull());
    }

    // It's okay for the cache to have the new value. The old ETag (see below) will cause cache refresh...
    assertThat(readCache(blob.getKey(), null), is(value3));

    // Cache and blob ETags must still be in old state.
    assertThat(readCache(blob.getKey(), ".properties"), containsString("etag=" + eTag1));
    assertThat(blob.getETag(), is(eTag1));
  }

  @Test
  public void testConflictResolution1() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);

    // Prepare the conflict.
    String value2 = "Different content";
    String eTag2 = writeServer(blob, value2);

    assertThat(blob.getContentsUTF(), is(value2));
    assertThat(blob.getETag(), is(eTag2));

    String value3 = "And now a non-conflicting string";
    blob.setContentsUTF(value3);

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value3));
    assertThat(blobInfo.eTag, is(blob.getETag()));
  }

  @Test
  public void testConflictResolution1WithCache() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN, cache);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);

    // Prepare the conflict.
    String value2 = "Different content";
    String eTag2 = writeServer(blob, value2);

    assertThat(blob.getContentsUTF(), is(value2));
    assertThat(blob.getETag(), is(eTag2));

    String value3 = "And now a non-conflicting string";
    blob.setContentsUTF(value3);
    assertThat(readCache(blob.getKey(), null), is(value3));
    assertThat(readCache(blob.getKey(), ".properties"), containsString("etag=" + blob.getETag()));
  }

  @Test
  public void testConflictResolution2() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);

    // Prepare the conflict.
    String value2 = "Different content";
    String eTag2 = writeServer(blob, value2);

    String value3 = "And now a conflicting string";

    try
    {
      blob.setContentsUTF(value3);
      fail("ConflictException expected");
    }
    catch (ConflictException expected)
    {
      blob.setETag(eTag2);
    }

    blob.setContentsUTF(value3);

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value3));
    assertThat(blobInfo.eTag, is(blob.getETag()));
  }

  @Test
  public void testConflictResolution2WithCache() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN, cache);
    IBlob blob = storage.getBlob(makeKey());

    String value1 = "A short UTF-8 string value";
    blob.setContentsUTF(value1);

    // Prepare the conflict.
    String value2 = "Different content";
    String eTag2 = writeServer(blob, value2);

    String value3 = "And now a conflicting string";

    try
    {
      blob.setContentsUTF(value3);
      fail("ConflictException expected");
    }
    catch (ConflictException expected)
    {
      blob.setETag(eTag2); // Delete cache.

      try
      {
        readCache(blob.getKey(), null);
        fail("FileNotFoundException expected");
      }
      catch (FileNotFoundException expected2)
      {
        // SUCCESS
      }

      try
      {
        readCache(blob.getKey(), ".properties");
        fail("FileNotFoundException expected");
      }
      catch (FileNotFoundException expected2)
      {
        // SUCCESS
      }
    }

    blob.setContentsUTF(value3);
    assertThat(readCache(blob.getKey(), null), is(value3));
    assertThat(readCache(blob.getKey(), ".properties"), containsString("etag=" + blob.getETag()));
  }

  @Test
  public void testReauthenticate() throws Exception
  {
    IStorage storage = factory.create(APPLICATION_TOKEN);
    IBlob blob = storage.getBlob(makeKey());

    String value = "A short UTF-8 string value";
    blob.setContentsUTF(value);

    if (hasLocalServer())
    {
      assertThat(server.getSessions().size(), is(1));
      server.getSessions().clear();
    }

    assertThat(blob.getContentsUTF(), is(value));

    BlobInfo blobInfo = readServer(blob);
    assertThat(blobInfo.contents, is(value));
    assertThat(blobInfo.eTag, is(blob.getETag()));

    if (hasLocalServer())
    {
      assertThat(server.getSessions().size(), is(1));
    }
  }

  private String readCache(String key, String extension) throws IOException
  {
    try
    {
      return IOUtil.readUTF(cache.getFile(APPLICATION_TOKEN, key, extension));
    }
    catch (RuntimeException ex)
    {
      Throwable cause = ex.getCause();
      if (cause instanceof IOException)
      {
        throw (IOException)cause;
      }

      throw ex;
    }
  }

  private BlobInfo readServer(IBlob blob) throws IOException
  {
    BlobInfo result = new BlobInfo();

    String applicationToken = blob.getStorage().getApplicationToken();
    String key = blob.getKey();

    if (hasLocalServer())
    {
      try
      {
        File blobFile = server.getUserFile(user, applicationToken, key, null);
        File etagFile = server.getUserFile(user, applicationToken, key, ".etag");
        if (!blobFile.isFile() || !etagFile.isFile())
        {
          return null;
        }

        result.contents = IOUtil.readUTF(blobFile);
        result.eTag = IOUtil.readUTF(etagFile);
      }
      catch (RuntimeException ex)
      {
        Throwable cause = ex.getCause();
        if (cause instanceof IOException)
        {
          throw (IOException)cause;
        }

        throw ex;
      }
    }
    else
    {
      IStorage tmpStorage = factory.create(applicationToken);
      IBlob tmpBlob = tmpStorage.getBlob(key);

      result.contents = tmpBlob.getContentsUTF();
      if (result.contents == null)
      {
        return null;
      }

      result.eTag = tmpBlob.getETag();
    }

    return result;
  }

  private String writeServer(IBlob blob, String value) throws IOException
  {
    String applicationToken = blob.getStorage().getApplicationToken();
    String key = blob.getKey();

    if (hasLocalServer())
    {
      String eTag = UUID.randomUUID().toString();
      IOUtil.writeUTF(server.getUserFile(user, applicationToken, key, ".etag"), eTag);
      IOUtil.writeUTF(server.getUserFile(user, applicationToken, key, null), value);
      return eTag;
    }

    IStorage tmpStorage = factory.create(applicationToken);
    IBlob tmpBlob = tmpStorage.getBlob(key);
    tmpBlob.setETag(blob.getETag());
    tmpBlob.setContentsUTF(value);
    return tmpBlob.getETag();
  }

  private String makeKey()
  {
    if (hasLocalServer())
    {
      return KEY;
    }

    StringBuilder builder = new StringBuilder("T" + UUID.randomUUID().toString());
    for (int i = 0; i < builder.length(); i++)
    {
      if (i == 25)
      {
        builder.setLength(25);
        break;
      }

      char c = builder.charAt(i);
      if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_'))
      {
        builder.replace(i, i + 1, "");
        --i;
      }
    }

    return builder.toString();
  }

  private boolean hasLocalServer()
  {
    return server != null;
  }

  /**
   * @author Eike Stepper
   */
  private static final class BlobInfo
  {
    public String eTag;

    public String contents;
  }

  /**
   * @author Eike Stepper
   */
  private static final class TestCache extends FileStorageCache
  {
    public TestCache(File folder)
    {
      super(folder);
    }

    @Override
    public File getFile(String applicationToken, String key, String extension)
    {
      return super.getFile(applicationToken, key, extension);
    }
  }
}

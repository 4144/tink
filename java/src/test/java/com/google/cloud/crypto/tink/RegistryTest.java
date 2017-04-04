// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.cloud.crypto.tink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.cloud.crypto.tink.TestUtil.DummyAeadKeyManager;
import com.google.cloud.crypto.tink.TestUtil.DummyMac;
import com.google.cloud.crypto.tink.TestUtil.DummyMacKeyManager;
import com.google.cloud.crypto.tink.TinkProto.KeyData;
import com.google.cloud.crypto.tink.TinkProto.KeyStatusType;
import com.google.cloud.crypto.tink.TinkProto.KeyTemplate;
import com.google.cloud.crypto.tink.TinkProto.Keyset;
import com.google.cloud.crypto.tink.TinkProto.OutputPrefixType;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.security.GeneralSecurityException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for Registry.
 */
@RunWith(JUnit4.class)
public class RegistryTest {
  private static class CustomMacKeyManager implements KeyManager<Mac, Message, Message> {
    public CustomMacKeyManager() {}

    @Override
    public Mac getPrimitive(ByteString proto) throws GeneralSecurityException {
      return new DummyMac(this.getClass().getSimpleName());
    }
    @Override
    public Mac getPrimitive(Message proto) throws GeneralSecurityException {
      return new DummyMac(this.getClass().getSimpleName());
    }
    @Override
    public Message newKey(ByteString template) throws GeneralSecurityException {
      throw new GeneralSecurityException("Not Implemented");
    }
    @Override
    public Message newKey(Message template) throws GeneralSecurityException {
      throw new GeneralSecurityException("Not Implemented");
    }
    @Override
    public KeyData newKeyData(ByteString serialized) throws GeneralSecurityException {
      return KeyData.newBuilder().setTypeUrl(this.getClass().getSimpleName()).build();
    }
    @Override
    public boolean doesSupport(String typeUrl) {  // supports same keys as DummyMacKeyManager
      return typeUrl.equals(DummyMacKeyManager.class.getSimpleName());
    }
  }

  private static class Mac2KeyManager implements KeyManager<Mac, Message, Message> {
    public Mac2KeyManager() {}

    @Override
    public Mac getPrimitive(ByteString proto) throws GeneralSecurityException {
      return new DummyMac(this.getClass().getSimpleName());
    }
    @Override
    public Mac getPrimitive(Message proto) throws GeneralSecurityException {
      return new DummyMac(this.getClass().getSimpleName());
    }
    @Override
    public Message newKey(ByteString template) throws GeneralSecurityException {
      throw new GeneralSecurityException("Not Implemented");
    }
    @Override
    public Message newKey(Message template) throws GeneralSecurityException {
      throw new GeneralSecurityException("Not Implemented");
    }
    @Override
    public KeyData newKeyData(ByteString serialized) throws GeneralSecurityException {
      return KeyData.newBuilder().setTypeUrl(this.getClass().getSimpleName()).build();
    }
    @Override
    public boolean doesSupport(String typeUrl) {
      return typeUrl.equals(this.getClass().getSimpleName());
    }
  }

  @Test
  public void testKeyManagerRegistration() throws Exception {
    Registry registry = new Registry();

    String mac1TypeUrl = DummyMacKeyManager.class.getSimpleName();
    String mac2TypeUrl = Mac2KeyManager.class.getSimpleName();
    String aeadTypeUrl = DummyAeadKeyManager.class.getSimpleName();

    // Register some key managers.
    registry.registerKeyManager(mac1TypeUrl, new DummyMacKeyManager());
    registry.registerKeyManager(mac2TypeUrl, new Mac2KeyManager());
    registry.registerKeyManager(aeadTypeUrl, new DummyAeadKeyManager());

    // Retrieve some key managers.
    KeyManager<Mac, Message, Message> mac1Manager = registry.getKeyManager(mac1TypeUrl);
    KeyManager<Mac, Message, Message> mac2Manager = registry.getKeyManager(mac2TypeUrl);
    assertEquals(DummyMacKeyManager.class, mac1Manager.getClass());
    assertEquals(Mac2KeyManager.class, mac2Manager.getClass());
    String computedMac = new String(mac1Manager.getPrimitive(
        ByteString.copyFrom(new byte[0])).computeMac(null), "UTF-8");
    assertEquals(DummyMacKeyManager.class.getSimpleName(), computedMac);
    computedMac = new String(mac2Manager.getPrimitive(
        ByteString.copyFrom(new byte[0])).computeMac(null), "UTF-8");
    assertEquals(Mac2KeyManager.class.getSimpleName(), computedMac);

    KeyManager<Aead, Message, Message> aeadManager = registry.getKeyManager(aeadTypeUrl);
    assertEquals(DummyAeadKeyManager.class, aeadManager.getClass());
    Aead aead = aeadManager.getPrimitive(ByteString.copyFrom(new byte[0]));
    String ciphertext = new String(aead.encrypt("plaintext".getBytes("UTF-8"), null), "UTF-8");
    assertTrue(ciphertext.contains(DummyAeadKeyManager.class.getSimpleName()));
    // TODO(przydatek): add tests when the primitive of KeyManager does not match key type.

    String badTypeUrl = "bad type URL";
    try {
      KeyManager<Mac, Message, Message> unused = registry.getKeyManager(badTypeUrl);
      fail("Expected GeneralSecurityException.");
    } catch (GeneralSecurityException e) {
      assertTrue(e.toString().contains("unsupported"));
      assertTrue(e.toString().contains(badTypeUrl));
    }
  }

  @Test
  public void testKeyAndPrimitiveCreation() throws Exception {
    Registry registry = new Registry();

    String mac1TypeUrl = DummyMacKeyManager.class.getSimpleName();
    String mac2TypeUrl = Mac2KeyManager.class.getSimpleName();
    String aeadTypeUrl = DummyAeadKeyManager.class.getSimpleName();

    // Register some key managers.
    registry.registerKeyManager(mac1TypeUrl, new DummyMacKeyManager());
    registry.registerKeyManager(mac2TypeUrl, new Mac2KeyManager());
    registry.registerKeyManager(aeadTypeUrl, new DummyAeadKeyManager());

    // Create some keys and primitives.
    KeyTemplate template =  KeyTemplate.newBuilder().setTypeUrl(mac2TypeUrl).build();
    KeyData key = registry.newKeyData(template);
    assertEquals(mac2TypeUrl, key.getTypeUrl());
    Mac mac = registry.getPrimitive(key);
    String computedMac = new String(mac.computeMac(null), "UTF-8");
    assertEquals(mac2TypeUrl, computedMac);

    template =  KeyTemplate.newBuilder().setTypeUrl(aeadTypeUrl).build();
    key = registry.newKeyData(template);
    assertEquals(aeadTypeUrl, key.getTypeUrl());
    Aead aead = registry.getPrimitive(key);
    String ciphertext = new String(aead.encrypt("plaintext".getBytes("UTF-8"), null), "UTF-8");
    assertTrue(ciphertext.contains(aeadTypeUrl));

    // Create a keyset, and get a PrimitiveSet.
    KeyTemplate template1 =  KeyTemplate.newBuilder().setTypeUrl(mac1TypeUrl).build();
    KeyTemplate template2 =  KeyTemplate.newBuilder().setTypeUrl(mac2TypeUrl).build();
    KeyData key1 = registry.newKeyData(template1);
    KeyData key2 = registry.newKeyData(template1);
    KeyData key3 = registry.newKeyData(template2);
    KeysetHandle keysetHandle = new KeysetHandle(Keyset.newBuilder()
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key1)
            .setKeyId(1)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key2)
            .setKeyId(2)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key3)
            .setKeyId(3)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .setPrimaryKeyId(2)
        .build());
    PrimitiveSet<Mac> macSet = registry.getPrimitives(keysetHandle);
    computedMac = new String(macSet.getPrimary().getPrimitive().computeMac(null), "UTF-8");
    assertEquals(mac1TypeUrl, computedMac);

    // Try a keyset with some keys non-ENABLED.
    keysetHandle = new KeysetHandle(Keyset.newBuilder()
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key1)
            .setKeyId(1)
            .setStatus(KeyStatusType.DESTROYED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key2)
            .setKeyId(2)
            .setStatus(KeyStatusType.DISABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key3)
            .setKeyId(3)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .setPrimaryKeyId(3)
        .build());
    macSet = registry.getPrimitives(keysetHandle);
    computedMac = new String(macSet.getPrimary().getPrimitive().computeMac(null), "UTF-8");
    assertEquals(mac2TypeUrl, computedMac);
  }


  @Test
  public void testRegistryCollisions() throws Exception {
    Registry registry = new Registry();
    String mac1TypeUrl = DummyMacKeyManager.class.getSimpleName();
    String mac2TypeUrl = Mac2KeyManager.class.getSimpleName();

    try {
      registry.registerKeyManager(mac1TypeUrl, null);
      fail("Expected NullPointerException.");
    } catch (NullPointerException e) {
      assertTrue(e.toString().contains("must be non-null"));
    }

    registry.registerKeyManager(mac1TypeUrl, new DummyMacKeyManager());
    registry.registerKeyManager(mac2TypeUrl, new Mac2KeyManager());

    // This should not overwrite the existing manager.
    assertFalse(registry.registerKeyManager(mac1TypeUrl, new Mac2KeyManager()));
    KeyManager<Mac, Message, Message> manager = registry.getKeyManager(mac1TypeUrl);
    assertEquals(DummyMacKeyManager.class.getSimpleName(),
        manager.getClass().getSimpleName());
  }

  @Test
  public void testInvalidKeyset() throws Exception {
    // Setup the registry.
    Registry registry = new Registry();
    String mac1TypeUrl = DummyMacKeyManager.class.getSimpleName();
    registry.registerKeyManager(mac1TypeUrl, new DummyMacKeyManager());

    // Empty keyset.
    try {
      registry.getPrimitives(new KeysetHandle(Keyset.newBuilder().build()));
      fail("Invalid keyset. Expect GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertTrue(e.toString().contains("empty keyset"));
    }

    // Create a keyset.
    KeyTemplate template1 =  KeyTemplate.newBuilder().setTypeUrl(mac1TypeUrl).build();
    KeyData key1 = registry.newKeyData(template1);
    // No primary key.
    KeysetHandle keysetHandle = new KeysetHandle(Keyset.newBuilder()
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key1)
            .setKeyId(1)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .build());
    // No primary key.
    try {
      registry.getPrimitives(keysetHandle);
      fail("Invalid keyset. Expect GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertTrue(e.toString().contains("keyset doesn't contain a valid primary key"));
    }

    // Primary key is disabled.
    keysetHandle = new KeysetHandle(Keyset.newBuilder()
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key1)
            .setKeyId(1)
            .setStatus(KeyStatusType.DISABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .setPrimaryKeyId(1)
        .build());
    try {
      registry.getPrimitives(keysetHandle);
      fail("Invalid keyset. Expect GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertTrue(e.toString().contains("keyset doesn't contain a valid primary key"));
    }
  }

  @Test
  public void testCustomKeyManagerHandling() throws Exception {
    // Setup the registry.
    Registry registry = new Registry();
    String mac1TypeUrl = DummyMacKeyManager.class.getSimpleName();
    String mac2TypeUrl = Mac2KeyManager.class.getSimpleName();

    registry.registerKeyManager(mac1TypeUrl, new DummyMacKeyManager());
    registry.registerKeyManager(mac2TypeUrl, new Mac2KeyManager());

    // Create a keyset.
    KeyTemplate template1 =  KeyTemplate.newBuilder().setTypeUrl(mac1TypeUrl).build();
    KeyTemplate template2 =  KeyTemplate.newBuilder().setTypeUrl(mac2TypeUrl).build();
    KeyData key1 = registry.newKeyData(template1);
    KeyData key2 = registry.newKeyData(template2);
    KeysetHandle keysetHandle = new KeysetHandle(Keyset.newBuilder()
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key1)
            .setKeyId(1)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .addKey(Keyset.Key.newBuilder()
            .setKeyData(key2)
            .setKeyId(2)
            .setStatus(KeyStatusType.ENABLED)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build())
        .setPrimaryKeyId(2)
        .build());
    // Get a PrimitiveSet using registered key managers.
    PrimitiveSet<Mac> macSet = registry.getPrimitives(keysetHandle);
    List<PrimitiveSet<Mac>.Entry<Mac>> mac1List =
        macSet.getPrimitive(keysetHandle.getKeyset().getKey(0));
    List<PrimitiveSet<Mac>.Entry<Mac>> mac2List =
        macSet.getPrimitive(keysetHandle.getKeyset().getKey(1));
    assertEquals(1, mac1List.size());
    assertEquals(mac1TypeUrl, new String(
        mac1List.get(0).getPrimitive().computeMac(null), "UTF-8"));
    assertEquals(1, mac2List.size());
    assertEquals(mac2TypeUrl, new String(
        mac2List.get(0).getPrimitive().computeMac(null), "UTF-8"));

    // Get a PrimitiveSet using a custom key manager for key1.
    KeyManager<Mac, Message, Message> customManager = new CustomMacKeyManager();
    macSet = registry.getPrimitives(keysetHandle, customManager);
    mac1List = macSet.getPrimitive(keysetHandle.getKeyset().getKey(0));
    mac2List = macSet.getPrimitive(keysetHandle.getKeyset().getKey(1));
    assertEquals(1, mac1List.size());
    assertEquals(CustomMacKeyManager.class.getSimpleName(),
        new String(mac1List.get(0).getPrimitive().computeMac(null), "UTF-8"));
    assertEquals(1, mac2List.size());
    assertEquals(mac2TypeUrl,
        new String(mac2List.get(0).getPrimitive().computeMac(null), "UTF-8"));
  }

  // TODO(przydatek): Add more tests for creation of PrimitiveSets.
}

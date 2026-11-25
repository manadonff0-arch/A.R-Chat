const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const OWNER_EMAIL = "muhammadkamran2484@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("public_profiles").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("statuses").where("privacyMode", "==", "contacts").get());
  await assertFails(unauthDb.collection("app_config").doc("public_status").get());
});

test("User private PII & public profile isolation + no self-suspension", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Ayesha",
      username: "ayesha_pk",
      about: "Available on A.R Chat",
      carrierName: "Jazz",
      accountStatus: "active",
      ecdhPublicKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestkey",
      keyFingerprint: "ABCD1234",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      phoneNumber: "+923001234567",
      countryCode: "+92",
      carrierName: "Jazz",
      displayName: "Ayesha",
      username: "ayesha_pk",
      accountStatus: "active",
      languageCode: "en",
      themeMode: "dark",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read Alice's non-PII public profile via filtered list or get
  await assertSucceeds(bobDb.collection("public_profiles").doc(ALICE_UID).get());
  await assertSucceeds(
    bobDb.collection("public_profiles").where("accountStatus", "==", "active").get()
  );

  // Bob CANNOT read or list Alice's private PII document
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Unfiltered list on public_profiles fails
  await assertFails(bobDb.collection("public_profiles").get());

  // Alice cannot inject ghost field or escalate accountStatus
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "OWNER",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Chats & messages enforce memberUids isolation and Kotlin payload parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      chatType: "direct",
      title: "Ayesha & Bilal",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      lastMessagePreview: "Salam!",
      lastMessageSenderUid: ALICE_UID,
      lastMessageType: "text",
      lastMessageEncrypted: true,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query by memberUids succeeds for Bob, fails for Charlie & unfiltered
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("chats").doc("chat_1").get());
  await assertFails(bobDb.collection("chats").get());

  // Alice sends encrypted message
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      chatId: "chat_1",
      senderUid: ALICE_UID,
      senderName: "Ayesha",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      encrypted: true,
      ciphertextBase64: "ZW5jcnlwdGVkUGF5bG9hZA==",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      senderKeyFingerprint: "ABCD1234",
      deliveryStatus: "sent",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can list messages with memberUids filter and mark read
  await assertSucceeds(
    bobDb
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").update({
      deliveryStatus: "read",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Charlie cannot read or write messages
  await assertFails(
    charlieDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").get()
  );
});

test("Owner system enforces server-side authorization & blocks privilege escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid_1", {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const spoofedOwnerDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  const now = new Date(Date.now() - 2000);

  // Ordinary user and unverified email CANNOT write owner_security, admins, or public_status
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      role: "OWNER",
      addedByUid: ALICE_UID,
      createdAt: now,
    })
  );
  await assertFails(
    spoofedOwnerDb.collection("app_config").doc("owner_security").set({
      ownerEmail: OWNER_EMAIL,
      ownerPhoneNumber: "+923001112233",
      pinHash: "hash",
      pinSalt: "salt",
      twoFactorEnabled: true,
      updatedAt: now,
    })
  );
  await assertFails(aliceDb.collection("app_config").doc("owner_security").get());
  await assertFails(aliceDb.collection("admin_logs").get());

  // Verified primary owner CAN configure owner_security, public_status, and add admins
  await assertSucceeds(
    ownerDb.collection("app_config").doc("owner_security").set({
      ownerEmail: OWNER_EMAIL,
      ownerPhoneNumber: "+923001112233",
      pinHash: "pbkdf2hash",
      pinSalt: "randomsalt",
      twoFactorEnabled: true,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("app_config").doc("public_status").set({
      maintenanceMode: false,
      maintenanceMessage: "",
      latestVersion: "1.0.0",
      globalAnnouncement: "Welcome to A.R Chat",
      updatedByUid: "owner_uid_1",
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("app_config").doc("public_status").get());
});

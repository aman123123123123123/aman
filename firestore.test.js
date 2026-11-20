const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_uid_001";
const CHILD_UID = "child_uid_002";
const STRANGER_UID = "stranger_uid_999";

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauth.collection("pairingCodes").doc("123456").get());
  await assertFails(
    unauth.collection("pairingCodes").doc("123456").set({
      childUid: CHILD_UID,
      deviceLabel: "Child Phone",
      status: "pending",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauth.collection("family_links").where("memberUids", "array-contains", PARENT_UID).get()
  );
});

test("Child creates pairingCode, listing pairingCodes fails, Parent claims and creates family_link", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const past = new Date(Date.now() - 2000);

  // Child creates pending pairing code
  await assertSucceeds(
    childDb.collection("pairingCodes").doc("482915").set({
      childUid: CHILD_UID,
      deviceLabel: "هاتف الطفل",
      status: "pending",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Listing pairingCodes must fail (anti-scraping)
  await assertFails(parentDb.collection("pairingCodes").get());

  // Non-6-digit code fails
  await assertFails(
    childDb.collection("pairingCodes").doc("abc").set({
      childUid: CHILD_UID,
      deviceLabel: "هاتف الطفل",
      status: "pending",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Parent reads specific code & claims it
  await assertSucceeds(parentDb.collection("pairingCodes").doc("482915").get());
  await assertSucceeds(
    parentDb.collection("pairingCodes").doc("482915").update({
      status: "claimed",
      parentUid: PARENT_UID,
      linkId: "link_482915",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Re-claiming an already claimed code fails (terminal state lock)
  await assertFails(
    strangerDb.collection("pairingCodes").doc("482915").update({
      status: "claimed",
      parentUid: STRANGER_UID,
      linkId: "link_evil",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Parent creates family_link referencing claimed code
  await assertSucceeds(
    parentDb.collection("family_links").doc("link_482915").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      childName: "أحمد",
      pairingCode: "482915",
      parentPin: "1234",
      activeRequestType: "none",
      childBatteryPercent: 100,
      childCharging: false,
      childStatusText: "متصل",
      cameraSharingActive: false,
      screenSharingActive: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Filtered memberUids query succeeds for parent & child; unfiltered fails; stranger fails
  await assertSucceeds(
    parentDb.collection("family_links").where("memberUids", "array-contains", PARENT_UID).get()
  );
  await assertSucceeds(
    childDb.collection("family_links").where("memberUids", "array-contains", CHILD_UID).get()
  );
  await assertFails(parentDb.collection("family_links").get());
  await assertFails(strangerDb.collection("family_links").doc("link_482915").get());
});

test("Messages & check-in frames enforce membership, field immutability, and role rules", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("family_links").doc("link_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      childName: "سارة",
      pairingCode: "112233",
      parentPin: "",
      activeRequestType: "none",
      childBatteryPercent: 85,
      childCharging: true,
      childStatusText: "جيد",
      cameraSharingActive: false,
      screenSharingActive: false,
      createdAt: past,
      updatedAt: past,
    });
  });

  // Parent sends camera check-in request
  await assertSucceeds(
    parentDb.collection("family_links").doc("link_1").update({
      activeRequestType: "camera_checkin",
      activeRequestAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Child cannot hijack parentPin
  await assertFails(
    childDb.collection("family_links").doc("link_1").update({
      parentPin: "9999",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Parent sends message; Child acknowledges
  await assertSucceeds(
    parentDb.collection("family_links").doc("link_1").collection("messages").doc("msg_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      senderUid: PARENT_UID,
      senderRole: "parent",
      title: "رسالة من الوالد",
      body: "حان وقت المذاكرة",
      acknowledged: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    childDb.collection("family_links").doc("link_1").collection("messages").doc("msg_1").update({
      acknowledged: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Child uploads camera frame; Parent cannot forge child frame; Stranger cannot read
  await assertSucceeds(
    childDb.collection("family_links").doc("link_1").collection("checkins").doc("chk_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      kind: "camera",
      imageBase64: "AQIDBA==",
      caption: "فحص كاميرا",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    parentDb.collection("family_links").doc("link_1").collection("checkins").doc("chk_fake").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      kind: "camera",
      imageBase64: "AQIDBA==",
      caption: "مزيف",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    strangerDb
      .collection("family_links")
      .doc("link_1")
      .collection("messages")
      .get()
  );
  await assertFails(
    strangerDb
      .collection("family_links")
      .doc("link_1")
      .collection("messages")
      .where("memberUids", "array-contains", PARENT_UID)
      .get()
  );
  await assertFails(
    strangerDb
      .collection("family_links")
      .doc("link_1")
      .collection("messages")
      .doc("msg_1")
      .get()
  );

  // Filtered list succeeds for parent
  await assertSucceeds(
    parentDb
      .collection("family_links")
      .doc("link_1")
      .collection("checkins")
      .where("memberUids", "array-contains", PARENT_UID)
      .get()
  );
});

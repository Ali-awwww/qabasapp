const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated access: rejected on users profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Unauthenticated access: rejected on saved items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saved_items").doc("item1").get());
});

test("Authenticated owner: can create and read own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user violation: Alice cannot read or write Bob's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      createdAt: new Date(),
    })
  );
});

test("Authenticated owner: can create, read, and delete saved Quran item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemDoc = aliceDb.collection("users").doc(ALICE_UID).collection("saved_items").doc("item_101");

  await assertSucceeds(
    itemDoc.set({
      id: "item_101",
      userId: ALICE_UID,
      itemType: "QURAN_AYAH",
      title: "آية الكرسي",
      content: "الله لا إله إلا هو الحي القيوم...",
      reference: "البقرة: 255",
      tags: ["قرآن", "أدعية"],
      createdAt: new Date(),
    })
  );

  await assertSucceeds(itemDoc.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saved_items").get());
  await assertSucceeds(itemDoc.delete());
});

test("Cross-user violation: Alice cannot read Bob's saved items", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("saved_items").doc("bob_item").set({
      id: "bob_item",
      userId: BOB_UID,
      itemType: "INSPIRATION",
      title: "حكمة",
      content: "كن جميلاً تر الوجود جميلا",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("saved_items").doc("bob_item").get()
  );
});

test("Schema validation: rejects invalid itemType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemDoc = aliceDb.collection("users").doc(ALICE_UID).collection("saved_items").doc("invalid_item");

  await assertFails(
    itemDoc.set({
      id: "invalid_item",
      userId: ALICE_UID,
      itemType: "INVALID_TYPE",
      title: "العنوان",
      content: "المحتوى",
      createdAt: new Date(),
    })
  );
});

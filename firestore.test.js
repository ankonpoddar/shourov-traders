const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_rice_owner";
const BOB_UID = "bob_other_owner";

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

function nowTs() {
  return new Date(Date.now() - 1000);
}

function validCustomer(uid) {
  return {
    userId: uid,
    name: "Rahim Rice Store",
    phone: "01711223344",
    shopAddress: "Badamtoli, Dhaka",
    openingBalance: 15000,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validTransaction(uid, customerId) {
  return {
    userId: uid,
    customerId,
    customerName: "Rahim Rice Store",
    type: "SALE",
    riceVariety: "Miniket",
    unit: "BAG",
    quantity: 10,
    costPricePerUnit: 3200,
    sellingPricePerUnit: 3500,
    totalAmount: 35000,
    paidAmount: 20000,
    dueAdded: 15000,
    grossProfit: 3000,
    transactionDateMillis: 1763290000000,
    notes: "50kg bags",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validExpense(uid) {
  return {
    userId: uid,
    frequency: "DAILY",
    category: "Transport",
    amount: 850,
    notes: "Van fare to Badamtoli",
    expenseDateMillis: 1763290000000,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validLoan(uid) {
  return {
    userId: uid,
    lenderName: "Islami Bank SME",
    principalAmount: 500000,
    annualInterestRate: 13.0,
    startDateMillis: 1760000000000,
    paidInstallmentsCount: 2,
    totalPrincipalRepaid: 40000,
    notes: "Working capital CC loan",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

test("Unauthenticated caller cannot read or write customers", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set(validCustomer(ALICE_UID))
  );
});

test("Owner can CRUD customers, transactions, expenses, and loans", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(aliceRef.collection("customers").doc("cust_1").set(validCustomer(ALICE_UID)));
  await assertSucceeds(aliceRef.collection("customers").get());
  await assertSucceeds(
    aliceRef.collection("customers").doc("cust_1").update({
      phone: "01899887766",
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(aliceRef.collection("transactions").doc("tx_1").set(validTransaction(ALICE_UID, "cust_1")));
  await assertSucceeds(aliceRef.collection("transactions").get());

  await assertSucceeds(aliceRef.collection("expenses").doc("exp_1").set(validExpense(ALICE_UID)));
  await assertSucceeds(aliceRef.collection("expenses").get());

  await assertSucceeds(aliceRef.collection("loans").doc("loan_1").set(validLoan(ALICE_UID)));
  await assertSucceeds(aliceRef.collection("loans").get());
});

test("Cross-user isolation: Bob cannot read or write Alice's collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set(validCustomer(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_2").set(validCustomer(BOB_UID))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_2").set(validTransaction(ALICE_UID, "cust_1"))
  );
});

test("Shadow update / ghost field and invalid schema payloads are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceRef = aliceDb.collection("users").doc(ALICE_UID);

  // Ghost field injection
  await assertFails(
    aliceRef.collection("customers").doc("cust_ghost").set({
      ...validCustomer(ALICE_UID),
      isVerified: true,
    })
  );

  // Spoofed userId inside document body
  await assertFails(
    aliceRef.collection("customers").doc("cust_spoof").set(validCustomer(BOB_UID))
  );

  // Negative expense amount
  await assertFails(
    aliceRef.collection("expenses").doc("exp_neg").set({
      ...validExpense(ALICE_UID),
      amount: -100,
    })
  );

  // Invalid interest rate > 100
  await assertFails(
    aliceRef.collection("loans").doc("loan_bad").set({
      ...validLoan(ALICE_UID),
      annualInterestRate: 150,
    })
  );
});

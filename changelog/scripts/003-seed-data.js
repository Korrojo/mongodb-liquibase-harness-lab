if (db.getName() !== 'liquibase_lab') {
  throw new Error('Refusing to seed outside liquibase_lab');
}

const fixtureMarker = 'mongodb-liquibase-harness';
const fixtures = [
  { _id: 'lab-001', sku: 'LAB-001', name: 'Synthetic notebook', labFixture: fixtureMarker },
  { _id: 'lab-002', sku: 'LAB-002', name: 'Synthetic pencil', labFixture: fixtureMarker },
  { _id: 'lab-003', sku: 'LAB-003', name: 'Synthetic folder', labFixture: fixtureMarker }
];

// Check every reserved ID before writing; do not overwrite an unrelated record.
for (const fixture of fixtures) {
  const existing = db.lab_items.findOne({ _id: fixture._id });
  if (existing && (existing.labFixture !== fixtureMarker || existing.sku !== fixture.sku)) {
    throw new Error('Reserved fixture ID already belongs to different data');
  }
}
for (const fixture of fixtures) {
  db.lab_items.updateOne({ _id: fixture._id }, { $setOnInsert: fixture }, { upsert: true });
}
const fixtureCount = db.lab_items.countDocuments({
  _id: { $in: fixtures.map(fixture => fixture._id) }, labFixture: fixtureMarker
});
if (fixtureCount !== fixtures.length) {
  throw new Error('Synthetic fixture verification failed');
}
print('Verified three synthetic lab fixtures');

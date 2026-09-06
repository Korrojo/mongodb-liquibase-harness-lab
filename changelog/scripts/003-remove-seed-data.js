if (db.getName() !== 'liquibase_lab') {
  throw new Error('Refusing to reverse fixtures outside liquibase_lab');
}
const fixtureFilter = {
  _id: { $in: ['lab-001', 'lab-002', 'lab-003'] },
  labFixture: 'mongodb-liquibase-harness'
};
db.lab_items.deleteMany(fixtureFilter);
if (db.lab_items.countDocuments(fixtureFilter) !== 0) {
  throw new Error('Synthetic fixture reversal verification failed');
}
print('Verified synthetic lab fixture reversal');

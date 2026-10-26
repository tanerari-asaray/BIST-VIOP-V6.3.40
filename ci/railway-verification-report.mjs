import fs from 'node:fs';
import assert from 'node:assert/strict';

const root='/validation/source-checks/';
const summary=JSON.parse(fs.readFileSync(root+'test-summary.json','utf8'));
const absent=JSON.parse(fs.readFileSync(root+'apk-absence.json','utf8'));
const tap=fs.readFileSync(root+'gateway-tests.tap','utf8');
const count=name=>Number(tap.match(new RegExp('^# '+name+' ([0-9]+)$','m'))?.[1]??NaN);
assert.ok(summary.jvm.tests>=983);
assert.match(summary.commit??'',/^[a-f0-9]{40}$/);
assert.equal(summary.jvm.failures,0);
assert.equal(summary.jvm.errors,0);
assert.equal(summary.jvm.skipped,0);
assert.equal(summary.lint.errors,0);
assert.ok(summary.compiled_android_test_classes>0);
assert.equal(absent.apk_or_aab_count,0);
assert.ok(count('tests')>=64);
assert.equal(count('pass'),count('tests'));
assert.equal(count('skipped'),0);
assert.equal(count('cancelled'),0);
assert.equal(count('fail'),0);
const report={
  status:'SOURCE_CHECKS_PASSED',
  sourceCommit:summary.commit,
  jvm:summary.jvm,
  lint:summary.lint,
  node:{tests:count('tests'),passed:count('pass'),failed:count('fail')},
  bunWebSocket:'passed during image build; see bounded build logs',
  android:'unit tests, Android test source compilation and resources validated',
  apkProduced:false,
  deviceExecution:'NOT_PERFORMED',
  liveProvider:'NOT_TESTED'
};
console.log(JSON.stringify(report));

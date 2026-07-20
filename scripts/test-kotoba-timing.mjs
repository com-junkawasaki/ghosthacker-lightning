import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

const artifactPath = process.argv[2];
const requireMultiArity = process.argv[3] === "multi-arity";
assert.ok(artifactPath, "compiled artifact path is required");
const generated = await import(pathToFileURL(artifactPath).href);
assert.deepEqual(generated.kotobaArtifact.requiredCapabilities, []);
const api = generated.instantiateKotoba({});

for (const value of [-600n, -1n, 0n, 1n, 600n]) {
  const expected = value < 0n ? -value : value;
  assert.equal(api["abs-ms"](value), expected);
}
const shotHit2 = api["shot-hit$arity$2"];
const shotHit3 = api["shot-hit$arity$3"] ?? api["shot-hit"];
if (requireMultiArity) {
  assert.equal(typeof shotHit2, "function");
  assert.equal(typeof api["shot-hit$arity$3"], "function");
}
for (const delta of [0n, 149n, 150n, 151n, 600n]) {
  if (shotHit2) assert.equal(shotHit2(1n, delta), delta <= 150n ? 1n : 0n);
  assert.equal(shotHit3(1n, delta, 150n), delta <= 150n ? 1n : 0n);
}
if (shotHit2) assert.equal(shotHit2(0n, 0n), 0n);
assert.equal(shotHit3(0n, 0n, 150n), 0n);
console.log("com-junkawasaki LIGHTNING Kotoba timing pilot passed");

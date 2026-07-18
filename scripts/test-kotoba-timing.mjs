import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

const artifactPath = process.argv[2];
assert.ok(artifactPath, "compiled artifact path is required");
const generated = await import(pathToFileURL(artifactPath).href);
assert.deepEqual(generated.kotobaArtifact.requiredCapabilities, []);
const api = generated.instantiateKotoba({});

for (const value of [-600n, -1n, 0n, 1n, 600n]) {
  const expected = value < 0n ? -value : value;
  assert.equal(api["abs-ms"](value), expected);
}
for (const delta of [0n, 149n, 150n, 151n, 600n]) {
  assert.equal(api["shot-hit"](1n, delta, 150n), delta <= 150n ? 1n : 0n);
}
assert.equal(api["shot-hit"](0n, 0n, 150n), 0n);
console.log("com-junkawasaki LIGHTNING Kotoba timing pilot passed");

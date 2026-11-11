import assert from "node:assert/strict";
import { calculateExactMath } from "../supabase/functions/studiomesa-ai-router/calculator.mjs";

const validCases = [
  ["2 + 3 * 4", "14"],
  ["(2 + 3) * 4", "20"],
  ["1,5 + 2,25", "3.75"],
  ["-5 + 3", "-2"],
  ["1 / 3", "0.333333333333"],
  ["2 * (3 + (4 - 1))", "12"],
  [" 12.5 / 2 ", "6.25"],
];

for (const [expression, expected] of validCases) {
  assert.equal(calculateExactMath(expression), expected, expression);
}

const invalidCases = [
  ["1 / 0", /Division by zero/],
  ["2 3", /Whitespace cannot split a number/],
  ["1.1234567890123", /at most 12 decimal places/],
  ["1000000000000001", /outside the supported range/],
  ["1e3", /unsupported characters/],
  ["(2 + 3", /Unbalanced parentheses/],
  ["2 ** 3", /Expected a number/],
  ["", /1 to 256 characters/],
  ["1".repeat(257), /1 to 256 characters/],
  [Array(66).fill("1").join("+"), /too many operations/],
];

for (const [expression, pattern] of invalidCases) {
  assert.throws(() => calculateExactMath(expression), pattern, expression);
}

console.log(`StudioMesa calculator tests passed: ${validCases.length} valid and ${invalidCases.length} invalid cases.`);

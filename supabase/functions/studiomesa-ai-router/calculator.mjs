export const CALCULATOR_TOOL = {
  type: "function",
  function: {
    name: "calculate_exact_math",
    description:
      "Calculates a short arithmetic expression. Supports decimal numbers, parentheses, unary signs, addition, subtraction, multiplication and division. Does not read or change restaurant data.",
    parameters: {
      type: "object",
      properties: {
        expression: {
          type: "string",
          description: "Arithmetic expression, for example (12.5 + 8) * 3",
          maxLength: 256,
        },
      },
      required: ["expression"],
      additionalProperties: false,
    },
  },
};

export function calculateExactMath(rawExpression) {
  if (!rawExpression || rawExpression.length > 256) {
    throw new Error("Expression must contain 1 to 256 characters.");
  }
  if (/\d\s+\d|\.\s+\d/.test(rawExpression)) {
    throw new Error("Whitespace cannot split a number.");
  }
  const expression = rawExpression.replace(/,/g, ".").replace(/\s+/g, "");
  const tokens = expression.match(/(?:\d+(?:\.\d*)?|\.\d+|[()+\-*/])/g) ?? [];
  if (tokens.length === 0 || tokens.join("") !== expression || tokens.length > 129) {
    throw new Error("Expression contains unsupported characters or is too complex.");
  }

  let index = 0;
  let operationCount = 0;
  const countOperation = () => {
    operationCount += 1;
    if (operationCount > 64) throw new Error("Expression contains too many operations.");
  };

  const parseExpression = () => {
    const parseFactor = () => {
      const token = tokens[index];
      if (token === "+" || token === "-") {
        index += 1;
        countOperation();
        const value = parseFactor();
        return token === "-" ? -value : value;
      }
      if (token === "(") {
        index += 1;
        const value = parseExpression();
        if (tokens[index] !== ")") throw new Error("Unbalanced parentheses.");
        index += 1;
        return value;
      }
      if (!token || !/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(token)) {
        throw new Error("Expected a number.");
      }
      index += 1;
      const value = Number(token);
      const decimalPlaces = token.includes(".") ? token.split(".")[1].length : 0;
      if (!Number.isFinite(value) || Math.abs(value) > 1e15) {
        throw new Error("Number is outside the supported range.");
      }
      if (decimalPlaces > 12) {
        throw new Error("Numbers may contain at most 12 decimal places.");
      }
      return value;
    };

    const parseTerm = () => {
      let value = parseFactor();
      while (tokens[index] === "*" || tokens[index] === "/") {
        const operator = tokens[index++];
        countOperation();
        const right = parseFactor();
        if (operator === "/" && right === 0) throw new Error("Division by zero.");
        value = operator === "*" ? value * right : value / right;
        if (!Number.isFinite(value) || Math.abs(value) > 1e15) {
          throw new Error("Result is outside the supported range.");
        }
      }
      return value;
    };

    let value = parseTerm();
    while (tokens[index] === "+" || tokens[index] === "-") {
      const operator = tokens[index++];
      countOperation();
      const right = parseTerm();
      value = operator === "+" ? value + right : value - right;
      if (!Number.isFinite(value) || Math.abs(value) > 1e15) {
        throw new Error("Result is outside the supported range.");
      }
    }
    return value;
  };

  const result = parseExpression();
  if (index !== tokens.length) throw new Error("Unexpected trailing input.");
  const rounded = Math.round((result + Number.EPSILON) * 1e12) / 1e12;
  return Number(rounded).toString();
}

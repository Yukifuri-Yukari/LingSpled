# Deprecated Designs
## 1. Shunting-Yard Expression Parsing Algorithm
```kotlin
val operators = ArrayDeque<Operator>()
val operands = ArrayDeque<Expression>()

operands.add(primary())
while (hasNext() && peek().type == TokenType.Operator) {
    val op = Operator.fromSymbol(next().text)!!

    while (
        operators.isNotEmpty() &&
        operators.last().priority >= op.priority
    ) {
        val r = operands.removeLast()
        val l = operands.removeLast()
        val op = operators.removeLast()
        operands.add(BinaryExpr(l, op, r))
    }
    operators.add(op)
    operands.add(primary())
}

while (operators.isNotEmpty()) {
    val right = operands.removeLast()
    val left = operands.removeLast()
    val op = operators.removeLast()
    operands.add(BinaryExpr(left, op, right))
}

return operands.removeLast()

```

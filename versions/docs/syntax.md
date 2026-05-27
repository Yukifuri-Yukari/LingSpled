# LingSpled 语法参考

本文档由解析器代码（`parser/subparser/`）推导，记录当前实际支持的语法特性。

---

## 顶层声明

### package
```
package a.b.c
```

### import
```
import a.b.C
import a.b.*          // 通配符
import a.b.C as Alias  // 别名
```

### 修饰符
可用于顶层声明、类成员：
`public` `private` `protected` `static` `final` `abstract` `open` `override` `sealed`

未显式写访问修饰符时自动补 `public`。

### 注解
```
@AnnotationName
@AnnotationName(arg1, arg2)
```

---

## 函数

```
fun name(param: Type, param2: Type = default): ReturnType {
    ...
}
```

- 返回类型省略时默认 `Unit`
- 支持泛型参数：`fun <T, U : Bound> name(...)`
- 支持默认参数值
- 函数体内可嵌套 `fun` 声明（局部函数）

---

## 变量

```
val name: Type = expr   // 不可变
var name: Type = expr   // 可变
val name = expr         // 类型推断
```

顶层、类内、函数体内均可声明。

---

## 类

### 基本结构
```
class Name<T> primaryCtorModifiers constructor(val x: Type, var y: Type = default) : SuperClass(args), Interface {
    val field: Type = expr
    var field2: Type

    fun method(): ReturnType { ... }

    constructor(params) {
        ...
    }
}
```

- 主构造函数参数中 `val`/`var` 直接声明字段
- 主构造函数可以省略 `constructor` 关键字
- 继承：`: SuperClass(superCtorArgs), Interface1, Interface2`
- 无显式继承时隐式继承 `Any`
- 次级构造函数用 `constructor(...)` 声明，可以有多个

### 接口
```
interface Name : ParentInterface1, ParentInterface2 {
    private val field: Type   // 字段必须 private

    fun abstractMethod(): Type          // 无体 → 自动 abstract
    fun defaultMethod(): Type { ... }   // 有体 → 默认实现
}
```

---

## 语句

### 赋值
```
x = expr
x += expr  // 复合赋值：+= -= *= /= %= &= |= ^=
obj.field = expr
arr[i] = expr
```

### 控制流
```
if (cond) { ... } else { ... }

for (init; cond; update) { ... }   // C 风格
while (cond) { ... }

break
continue
return expr
defer expr
```

### 方法/函数调用
```
func(args)
obj.method(args)
obj.method<TypeArgs>(args)
```

---

## 表达式

### 字面量
| 类型 | 示例 |
|:---:|:---|
| 整数 | `42` `0b1010` `0xFF` |
| 小数 | `3.14` |
| 布尔 | `true` `false` |
| 字符串 | `"hello"` `"""multiline"""` |

### 字符串插值
```
"Hello, $name!"
"Result: ${expr + 1}"
```

### 运算符（优先级从高到低）

| 优先级 | 运算符 | 说明 |
|:-----:|:------|:----|
| 1 | `!` `~` `++` `--` `+` `-` | 一元前缀 |
| 2 | `*` `/` `%` | 乘除 |
| 3 | `+` `-` | 加减 |
| 4 | `..` | 范围 |
| 5 | `<<` `>>` `>>>` | 位移 |
| 6 | `<` `>` `<=` `>=` `is` `!is` `in` `!in` | 比较 |
| 7 | `==` `!=` | 相等 |
| 8 | `&` | 位与 |
| 9 | `^` | 位异或 |
| 10 | `\|` | 位或 |
| 11 | `&&` | 逻辑与 |
| 12 | `\|\|` | 逻辑或 |
| 13 | `?:` | Elvis |
| 14 | `=` `+=` `-=` `*=` `/=` `%=` `&=` `\|=` `^=` | 赋值 |

后缀链（从左到右，高于所有二元运算符）：
- `expr.field` — 字段访问
- `expr.method(args)` — 方法调用
- `expr(args)` — 调用表达式（lambda/函数引用）
- `expr[index]` — 索引访问
- `expr++` / `expr--` — 后缀自增/自减
- `expr as Type` — 类型转换

### Lambda
```
{ body }                          // 无参，无类型
{ -> body }                       // 显式零参
{ x, y -> body }                  // 有参，类型推断
{ x: Int, y: Int -> body }        // 有参，有类型
```

### this
```
this
this.field
this.method(args)
```

---

## when 表达式

```
// 无主语
when {
    expr1 -> body
    expr2 if guard -> body
    is TypeName -> body
    is TypeName(a, b) -> body      // 解构
    is TypeName if guard -> body   // 带守卫
    else -> body
}

// 有主语
when (subject) {
    value -> body
    is TypeName -> body
    else -> body
}
```

`when` 可作表达式（用于赋值）或语句。

---

## 类型

```
Type              // 普通类型
Type?             // 可空类型
(A, B) -> C      // 函数类型
Name<T, U>        // 泛型实例化
```

泛型声明：`<T>` `<T, U : Bound>`
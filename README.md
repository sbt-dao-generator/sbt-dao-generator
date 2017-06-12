# sbt-dao-generator

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/septeni-original/sbt-dao-generator?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/septeni-original/sbt-dao-generator.svg)](https://travis-ci.org/septeni-original/sbt-dao-generator)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/jp.co.septeni-original/sbt-dao-generator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/jp.co.septeni-original/sbt-dao-generator)
[![Scaladoc](http://javadoc-badge.appspot.com/jp.co.septeni-original/sbt-dao-generator_2.10.svg?label=scaladoc)](http://javadoc-badge.appspot.com/jp.co.septeni-original/sbt-dao-generator_2.10)
[![Reference Status](https://www.versioneye.com/java/jp.co.septeni-original:sbt-dao-generator_2.10/reference_badge.svg?style=flat)](https://www.versioneye.com/java/jp.co.septeni-original:sbt-dao-generator_2.10/references)

## プラグインのビルド方法

```sh
$ sbt clean package
```

## プラグインの利用方法

project/plugins.sbtに以下のエントリを追加してください。

- リリース版を利用する場合

```scala
resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"

addSbtPlugin("jp.co.septeni-original" % "sbt-dao-generator" % "1.0.3")
```

- スナップショット版を利用する場合

```scala
resolvers += "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/"

addSbtPlugin("jp.co.septeni-original" % "sbt-dao-generator" % "1.0.4-SNAPSHOT")
```

## プラグインの設定方法

以下を参考にbuild.sbtに設定を行ってください。

```scala
// JDBCのドライバークラス名を指定します(必須)
driverClassName in generator := "org.h2.Driver"

// JDBCの接続URLを指定します(必須)
jdbcUrl in generator := "jdbc:h2:file:./target/test"

// JDBCの接続ユーザ名を指定します(必須)
jdbcUser in generator := "sa"

// JDBCの接続ユーザのパスワードを指定します(必須)
jdbcPassword in generator := ""

// スキーマ名を指定できます(任意。デフォルトは以下)
schemaName in generator := None,

// カラム型名をどのクラスにマッピングするかを決める関数を記述します(必須)
typeNameMapper in generator := {
  case "INTEGER" => "Int"
  case "VARCHAR" => "String"
  case "BOOLEAN" => "Boolean"
  case "DATE" | "TIMESTAMP" => "java.util.Date"
  case "DECIMAL" => "BigDecimal"
}

// 出力対象のテーブルをフィルターする関数を記述できます(任意。デフォルトはすべてのテーブルが対象になります)
tableNameFilter in generator := { tableName: String => tableName.toUpperCase != "SCHEMA_VERSION"}

// テンプレートファイルを配置するディレクトリを指定できます(任意。デフォルトは以下)
templateDirectory in generator := baseDirectory.value / "templates"

// ソースコードを出力するディレクトリを動的に変更することができます(任意。デフォルトは以下)
outputDirectoryMapper in generator := { className: String => (sourceManaed in Compile).value }

outputDirectoryMapper in generator := { className: String =>
  className match {
    case s if s.endsWith("Spec") => (sourceManaged in Test).value
    case s => (sourceManaged in Compile).value
  }
}

// テーブル名からモデルクラス名にマッピングする関数を記述できます(任意。デフォルトは以下)
classNameMapper in generator := { tableName: String =>
    Seq(StringUtil.camelize(tableName))
}

// 複数の出力ファイルを指定したい場合は以下のようにできます。
classNameMapper in generator := {
  case "DEPT" => Seq("Dept", "DeptSpec")
  case "EMP" => Seq("Emp", "EmpSpec")
}

// カラム名からプロパティ名にマッピングする関数を記述できます(任意。デフォルトは以下)
propertyNameMapper in generator := { columnName: String =>
    StringUtil.decapitalize(StringUtil.camelize(columnName))
}

// モデル名に対してどのテンプレートを利用するか指定できます(任意。デフォルトは以下)
templateNameMapper in generator := { className: String => "template.ftl" },

// モデル名に対してどのテンプレートを利用するか指定できます。
templateNameMapper in generator := {
  case className if className.endsWith("Spec") => "template_spec.ftl"
  case _ => "template.ftl"
}
```

## テンプレートファイル

templateName in generatorで指定されたテンプレートファイルを適宜編集してください。テンプレートエンジンは[FreeMarker](http://freemarker.org/)となります。

```
case class ${className}(
<#list allColumns as column>
<#if column.nullable>
${column.propertyName}: Option[${column.propertyTypeName}]<#if column_has_next>,</#if>
<#else>
${column.propertyName}: ${column.propertyTypeName}<#if column_has_next>,</#if>
</#if>
</#list>
) {

}
```

**テンプレートコンテキスト**

| 変数名      | 型 | 内容     |
|:-----------|:---|:---------|
| tableName | String | テーブル名 (USER_NAME)|
| className  | String | クラス名　(UserName) |
| decapitalizedClassName | String | デキャピタライズ・クラス名 (userName) |
| primaryKeys | カラムオブジェクトのリスト | プライマリーキー群 |
| columns | カラムオブジェクトのリスト | カラム群 |
| allColumns | カラムオブジェクトのリスト | すべてのカラム群(プライマリキー含む) |

**カラムオブジェクト**

| 変数名      | 内容     |
|:-----------|:---------|
| columnName | カラム名 (FIRST_NAME) |
| columnTypeName | カラムタイプ (VARCHAR, DATETIME, ...) |
| propertyName | プロパティ名 (firstName) |
| propertyTypeName | プロパティタイプ (String, java.util.Date) |
| capitalizedPropertyName | キャタライズされたクラス名 (FirstName) |
| nullable | NULL許容か否か(Boolean) |

## コード生成

すべてのテーブルを対象にする場合

```sh
$ sbt generator::generateAll
<snip>
[info] tableName = DEPT, generate file = /Users/sbt-user/myproject/target/scala-2.10/src_managed/Dept.scala
[info] tableName = EMP, generate file = /Users/sbt-user/myproject/target/scala-2.10/src_managed/Emp.scala
[success] Total time: 0 s, completed 2015/06/24 18:17:20
```

指定した複数テーブルを対象にする場合

```sh
$ sbt generator::generateMany DEPT EMP
<snip>
[info] tableNames = EMP, DEPT
[info] tableName = DEPT, generate file = /Users/sbt-user/myproject/target/scala-2.10/src_managed/Dept.scala
[info] tableName = EMP, generate file = /Users/sbt-user/myproject/target/scala-2.10/src_managed/Emp.scala
[success] Total time: 0 s, completed 2015/06/24 18:17:20
```


指定した単一テーブルを対象にする場合

```sh
$ sbt generator::generateOne DEPT
<snip>
[info] tableName = DEPT
[info] tableName = DEPT, generate file = /Users/sbt-user/myproject/target/scala-2.10/src_managed/Dept.scala
[success] Total time: 0 s, completed 2015/06/24 18:17:20
```

`sbt compile`時に`generator::generateAll`を実行したい場合は、build.sbtに以下を追加する。

```scala
sourceGenerators in Compile <+= generateAll in generator
```
 
## 開発者向け

### 単体テスト方法

プロジェクト直下に配置されたtest.mv.dbを使って単体テストを行います(test.mv.dbはcreate_table.sqlによって手動で作られたデータベースファイルです)。

```sh
$ sbt clean test
```

### プラグインの動作テスト方法

#### scriptedでテストを行う

Scripted Test Frameworkを使ったテストします。詳しくは[こちら](http://eed3si9n.com/ja/testing-sbt-plugins)を参考にしてください。
なお、生成されたソースコードは確認できません。

```
$ sbt clean scripted 
[info] Loading project definition from /Users/sbt-user/sbt-dao-generator/project
<snip>
Running sbt-dao-generator / simple
[info] Getting org.scala-sbt sbt 0.13.8 ...
[info] :: retrieving :: org.scala-sbt#boot-app
[info] 	confs: [default]
[info] 	52 artifacts copied, 0 already retrieved (17674kB/132ms)
[info] Getting Scala 2.10.4 (for sbt)...
[info] :: retrieving :: org.scala-sbt#boot-scala
[info] 	confs: [default]
[info] 	5 artifacts copied, 0 already retrieved (24459kB/78ms)
[info] [info] Loading project definition from /private/var/folders/tw/2_9djq693wj1889trb760g6w0000gn/T/sbt_9548a4f7/simple/project
[info] [info] Set current project to simple (in build file:/private/var/folders/tw/2_9djq693wj1889trb760g6w0000gn/T/sbt_9548a4f7/simple/)
[info] [info] Updating {file:/private/var/folders/tw/2_9djq693wj1889trb760g6w0000gn/T/sbt_9548a4f7/simple/}simple...
[info] [info] Resolving org.scala-lang#scala-library;2.10.4 ...
       [info] Resolving com.h2database#h2;1.4.187 ...
       [info] Resolving org.scala-lang#scala-compiler;2.10.4 ...
       [info] Resolving org.scala-lang#scala-reflect;2.10.4 ...
       [info] Resolving org.scala-lang#jline;2.10.4 ...
       [info] Resolving org.fusesource.jansi#jansi;1.4 ...
[info] [info] Done updating.
[info] [info] Flyway 3.2.1 by Boxfuse
[info] [info] Database: jdbc:h2:file:./target/test (H2 1.4)
[info] [info] Validated 1 migration (execution time 00:00.021s)
[info] [info] Current version of schema "PUBLIC": 1
[info] [info] Schema "PUBLIC" is up to date. No migration necessary.
[info] [success] Total time: 0 s, completed 2015/06/26 12:54:49
[info] [info] tableName = DEPT, generate file = /private/var/folders/tw/2_9djq693wj1889trb760g6w0000gn/T/sbt_9548a4f7/simple/target/scala-2.10/src_managed/Dept.scala
[info] [info] tableName = EMP, generate file = /private/var/folders/tw/2_9djq693wj1889trb760g6w0000gn/T/sbt_9548a4f7/simple/target/scala-2.10/src_managed/Emp.scala
[info] [success] Total time: 0 s, completed 2015/06/26 12:54:50
[info] + sbt-dao-generator / simple
[success] Total time: 23 s, completed 2015/06/26 12:54:50
```

#### 実際にプラグインをロードして実行する方法

実際に生成されたソースコード確認できます。

```sh
$ sbt -Dplugin.version=1.0.0-SNAPSHOT                                                                                                         
[info] Loading project definition from /Users/sbt-user/sbt-dao-generator/src/sbt-test/sbt-dao-generator/simple/project
[info] Set current project to simple (in build file:/Users/sbt-user/sbt-dao-generator/src/sbt-test/sbt-dao-generator/simple/)
> flywayMigrate
[info] Flyway 3.2.1 by Boxfuse
[info] Database: jdbc:h2:file:./target/test (H2 1.4)
[info] Validated 1 migration (execution time 00:00.020s)
[info] Current version of schema "PUBLIC": 1
[info] Schema "PUBLIC" is up to date. No migration necessary.
[success] Total time: 0 s, completed 2015/06/24 18:17:12
> generator::generateAll
[info] tableName = DEPT, generate file = /Users/sbt-user/sbt-dao-generator/src/sbt-test/sbt-dao-generator/simple/target/scala-2.10/src_managed/Dept.scala
[info] tableName = EMP, generate file = /Users/sbt-user/sbt-dao-generator/src/sbt-test/sbt-dao-generator/simple/target/scala-2.10/src_managed/Emp.scala
[success] Total time: 0 s, completed 2015/06/24 18:17:20
```

package jp.co.septeni_original.sbt.dao.generator.model

case class TableDesc(tableName: String, primaryDescs: Seq[PrimaryKeyDesc], columnDescs: Seq[ColumnDesc])


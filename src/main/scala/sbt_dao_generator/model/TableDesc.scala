package sbt_dao_generator.model

case class TableDesc(tableName: String, primaryDescs: Seq[PrimaryKeyDesc], columnDescs: Seq[ColumnDesc])

package jp.co.septeni_original.sbt.dao.generator.model

case class PrimaryKeyDesc(
    columnName: String,
    @deprecated("ColumnDesc#autoIncrementへ移動") autoIncrement: Boolean
)

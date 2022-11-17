// --- template_a
case class ${className}(
<#list columns as column>
<#if column.nullable>
${column.propertyName}: Option[${column.propertyTypeName}]<#if column_has_next>,</#if>
<#else>
${column.propertyName}: ${column.propertyTypeName}<#if column_has_next>,</#if>
</#if>
</#list>
) {


}

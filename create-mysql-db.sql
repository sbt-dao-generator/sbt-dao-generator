DROP DATABASE IF EXISTS `sbt_dao_gen`;
CREATE DATABASE `sbt_dao_gen` DEFAULT CHARSET utf8 COLLATE utf8_bin;
GRANT ALL PRIVILEGES ON `sbt_dao_gen`.* TO sbt_dao_gen@localhost IDENTIFIED BY 'passwd';
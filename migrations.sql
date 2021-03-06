--liquibase formatted sql

--changeset tpetr:1 dbms:mysql
CREATE TABLE `taskHistory` (
  `taskId` varchar(200) NOT NULL DEFAULT '',
  `requestId` varchar(100) NOT NULL,
  `status` varchar(50) NOT NULL,
  `pendingType` varchar(50) NOT NULL,
  `createdAt` timestamp NOT NULL DEFAULT '1971-01-01 00:00:01',
  `lastTaskStatus` varchar(100) DEFAULT NULL,
  `updatedAt` timestamp NULL DEFAULT NULL,
  `directory` varchar(500) DEFAULT NULL,
  `task` blob NOT NULL,
  PRIMARY KEY (`taskId`),
  KEY `requestId` (`requestId`,`createdAt`),
  KEY `requestId_2` (`requestId`,`updatedAt`),
  KEY `requestId_3` (`requestId`,`lastTaskStatus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `requestHistory` (
  `requestId` varchar(100) NOT NULL,
  `createdAt` timestamp NOT NULL DEFAULT '1971-01-01 00:00:01',
  `requestState` varchar(25) NOT NULL,
  `user` varchar(100) DEFAULT NULL,
  `request` blob NOT NULL,
  PRIMARY KEY (`requestId`,`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `taskUpdates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `taskId` varchar(200) DEFAULT NULL,
  `status` varchar(100) NOT NULL,
  `message` varchar(200) DEFAULT NULL,
  `createdAt` timestamp NOT NULL DEFAULT '1971-01-01 00:00:01',
  PRIMARY KEY (`id`),
  KEY `taskId` (`taskId`)
) ENGINE=InnoDB AUTO_INCREMENT=197678 DEFAULT CHARSET=utf8;

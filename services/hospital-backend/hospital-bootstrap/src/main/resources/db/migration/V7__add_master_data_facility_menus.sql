INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-building','楼宇管理','/master/buildings','FacilityMasterData',25 FROM sys_permission WHERE permission_code='hospital:campus:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-floor','楼层管理','/master/floors','FacilityMasterData',26 FROM sys_permission WHERE permission_code='hospital:campus:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-outpatient-department','门诊科室','/master/outpatient-departments','FacilityMasterData',27 FROM sys_permission WHERE permission_code='hospital:department:list';

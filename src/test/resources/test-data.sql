delete from favorite_features;
delete from comments;
delete from features;
delete from releases;
delete from products;

insert into products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) values
(1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java', 'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00'),
(2, 'goland','GO','GoLand', 'JetBrains IDE for Go', 'https://resources.jetbrains.com/storage/products/company/brand/logos/GoLand.png',false, 'admin','2024-03-01 00:00:00'),
(3, 'webstorm','WEB','WebStorm', 'JetBrains IDE for Web Development','https://resources.jetbrains.com/storage/products/company/brand/logos/WebStorm.png', false, 'admin','2024-03-01 00:00:00'),
(4, 'pycharm','PY','PyCharm', 'JetBrains IDE for Python', 'https://resources.jetbrains.com/storage/products/company/brand/logos/PyCharm.png',false, 'admin','2024-03-01 00:00:00'),
(5, 'rider','RIDER','Rider', 'JetBrains IDE for .NET', 'https://resources.jetbrains.com/storage/products/company/brand/logos/Rider.png',false, 'admin','2024-03-01 00:00:00')
;

insert into releases (id, product_id, code, description, status, planned_start_date, planned_release_date, actual_release_date, owner, notes, created_by, created_at, updated_by, updated_at) values
(1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8 - Bug fixes and performance improvements', 'RELEASED', '2023-10-01 09:00:00', '2023-11-15 17:00:00', '2023-11-10 10:00:00', 'jane.smith', 'Stable release with critical bug fixes', 'admin','2023-03-25 00:00:00', 'jane.smith', '2023-11-10 10:30:00'),
(2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3 - New features and improvements', 'COMPLETED', '2024-01-10 09:00:00', '2024-02-20 17:00:00', null, 'john.doe', 'Major release with new AI-powered features', 'admin','2024-02-25 00:00:00', 'john.doe', '2024-02-25 14:30:00'),
(3, 1, 'IDEA-2024.2.4', 'IntelliJ IDEA 2024.2.4 - New features and improvements', 'IN_PROGRESS', '2024-02-21 09:00:00', '2024-04-20 17:00:00', null, 'john.doe', 'Minor release with more features', 'admin','2024-02-25 00:00:00', 'john.doe', '2024-02-25 14:30:00'),
(4, 2, 'GO-2024.2.3', 'GoLand 2024.2.4 - Enhanced tooling - Without planned release date', 'RELEASED', '2024-01-05 09:00:00', null, '2024-02-15 11:00:00', 'jane.smith', 'Enhanced debugging and profiling tools', 'admin','2024-02-15 00:00:00', 'jane.smith', '2024-02-15 11:30:00'),
(5, 3, 'WEB-2024.2.3', 'WebStorm 2024.2.4 - Web development tools - 13 days delay', 'RELEASED', '2024-01-08 09:00:00', '2024-02-16 09:00:00', '2024-02-29 09:00:00', 'jane.smith', 'Improved TypeScript and React support', 'admin','2024-02-29 00:00:00', 'jane.smith', '2024-02-29 09:30:00'),
(6, 4, 'PY-2024.2.3', 'PyCharm 2024.2.4 - Python development - 14 days delay', 'DELAYED', '2024-01-12 09:00:00', '2024-02-18 17:00:00', '2024-03-03 17:00:00', 'jane.smith', 'Enhanced data science and ML support', 'admin','2024-03-03 00:00:00', 'jane.smith', '2024-03-03 17:30:00'),
(7, 4, 'PY-2024.2.4', 'PyCharm 2024.3.0 - Cancelled release', 'CANCELLED', '2024-04-01 09:00:00', '2024-06-15 17:00:00', null, 'john.doe', 'Planning phase for next major release', 'admin','2024-03-15 00:00:00', 'john.doe', '2024-03-15 10:00:00'),
(8, 4, 'PY-2024.3.0', 'PyCharm 2024.3.0 - Draft release', 'DRAFT', '2024-04-01 09:00:00', '2024-06-15 17:00:00', null, 'john.doe', 'Planning phase for next major release', 'admin','2024-03-15 00:00:00', 'john.doe', '2024-03-15 10:00:00'),
(9, 4, 'PY-2024.3.1', 'PyCharm 2024.3.1 - Planned release', 'PLANNED', '2024-05-01 09:00:00', '2024-07-20 17:00:00', null, 'jane.smith', 'Planned for Q3 release', 'admin','2024-04-01 00:00:00', 'jane.smith', '2024-04-01 10:00:00'),
(10, 5, 'RIDER-2024.2.6', 'Rider 2024.2.6 - .NET development', 'RELEASED', '2024-01-14 09:00:00', '2024-02-20 17:00:00', '2024-02-16 15:00:00', 'jane.smith', 'Improved C# and .NET framework support', 'admin','2024-02-16 00:00:00', 'jane.smith', '2024-02-16 15:30:00')
;

insert into features (id, product_id, release_id, code, title, description, status, created_by, assigned_to, created_at) values
(1, 1, 1, 'IDEA-1', 'Redesign Structure Tool Window', 'Redesign Structure Tool Window to show logical structure', 'NEW', 'siva', 'marcobehler', '2024-02-24'),
(2, 1, 1, 'IDEA-2', 'SDJ Repository Method AutoCompletion', 'Spring Data JPA Repository Method AutoCompletion as you type', 'NEW', 'daniiltsarev', 'siva', '2024-03-14'),
(3, 2, null, 'GO-3', 'Make Go to Type and Go to Symbol dumb aware', 'Make Go to Type and Go to Symbol dumb aware', 'IN_PROGRESS', 'antonarhipov', 'andreybelyaev', '2024-01-14'),
(4, 1, 2, 'IDEA-3', 'AI Code Completion', 'Enhanced AI-powered code completion', 'RELEASED', 'admin', 'john.doe', '2024-02-20'),
(5, 1, 3, 'IDEA-4', 'Kotlin Multiplatform Support', 'Improved Kotlin multiplatform project support', 'IN_PROGRESS', 'admin', 'john.doe', '2024-02-22'),
(6, 2, 4, 'GO-4', 'Go 1.22 Support', 'Full support for Go 1.22 features', 'RELEASED', 'admin', 'jane.smith', '2024-02-10'),
(7, 3, 5, 'WEB-4', 'Vue 3 Enhanced Support', 'Better Vue 3 composition API support', 'RELEASED', 'admin', 'jane.smith', '2024-02-20'),
(8, 4, 6, 'PY-4', 'Python 3.12 Support', 'Support for Python 3.12 new features', 'RELEASED', 'admin', 'jane.smith', '2024-03-01'),
(9, 4, 7, 'PY-5', 'Django 5.0 Support', 'Enhanced Django 5.0 framework support', 'NEW', 'admin', 'john.doe', '2024-03-10'),
(10, 4, 9, 'PY-6', 'Data Science Tools', 'Enhanced data science and ML tooling', 'ON_HOLD', 'admin', 'jane.smith', '2024-04-02'),
-- MEDIUM risk: PY-2024.2.3 (release 6) - 5 features with 1 ON_HOLD = 20%
(11, 4, 6, 'PY-7', 'Type Hints Enhancement', 'Improved type hints support', 'NEW', 'admin', 'jane.smith', '2024-03-02'),
(12, 4, 6, 'PY-8', 'Debugger Improvements', 'Enhanced debugging capabilities', 'NEW', 'admin', 'jane.smith', '2024-03-02'),
(13, 4, 6, 'PY-9', 'Test Runner Update', 'Updated test runner', 'IN_PROGRESS', 'admin', 'jane.smith', '2024-03-02'),
(14, 4, 6, 'PY-10', 'Virtual Env Support', 'Better virtual environment support', 'ON_HOLD', 'admin', 'jane.smith', '2024-03-02'),
-- LOW risk: IDEA-2024.2.4 (release 3) - 11 features with 1 ON_HOLD = 9.09%
(15, 1, 3, 'IDEA-5', 'Code Completion Enhancement', 'Enhanced code completion', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(16, 1, 3, 'IDEA-6', 'Refactoring Tools', 'New refactoring tools', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(17, 1, 3, 'IDEA-7', 'Git Integration', 'Improved Git integration', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(18, 1, 3, 'IDEA-8', 'Database Tools', 'Enhanced database tools', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(19, 1, 3, 'IDEA-9', 'Docker Support', 'Docker integration', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(20, 1, 3, 'IDEA-10', 'Kubernetes Support', 'Kubernetes integration', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(21, 1, 3, 'IDEA-11', 'Cloud Integration', 'Cloud provider integration', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(22, 1, 3, 'IDEA-12', 'Performance Profiler', 'Built-in performance profiler', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(23, 1, 3, 'IDEA-13', 'Memory Analyzer', 'Memory analysis tools', 'NEW', 'admin', 'john.doe', '2024-02-22'),
(24, 1, 3, 'IDEA-14', 'Spring Boot Integration', 'Enhanced Spring Boot support', 'ON_HOLD', 'admin', 'john.doe', '2024-02-22')
;

insert into favorite_features (id, feature_id, user_id) values
(1, 2, 'user');

insert into comments (id, feature_id, created_by, content) values
(1, 1, 'user', 'This is a comment on feature IDEA-1'),
(2, 1, 'user', 'This is a comment on feature IDEA-2'),
(3, 1, 'user', 'This is a comment on feature GO-3');

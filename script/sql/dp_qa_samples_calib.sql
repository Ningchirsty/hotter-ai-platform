-- ------------------------------------------------------------------
-- QA 校准样本落库（R2.4）
--
-- 样本来源如实说明：**不是模型生成**，而是对项目里的真实产品参考图做程序化受控扰动
-- （亮度/对比度微调 = 正样本；色相偏移/去饱和/上下翻转 = 改产品事实；水印/模糊/噪点 = 画面瑕疵；
--  裁切/拉伸/镜像 = 构图偏差）。因为扰动方式已知，ground truth 可靠。
--
-- 用途：把「成品一致性检查在当前部署下能抓住什么、抓不住什么」固化下来，
-- 以后换模型或调阈值时跑同一批样本做回归对比（见 QA样本校准报告.md）。
--
-- 幂等：按 sample_no 判重，可重复执行。
-- 备注：sample_no 由脚本按日期生成，重跑同日会得到同一批编号（不会重复插入）。
-- ------------------------------------------------------------------
-- 由 calibrate-qa-samples.py 生成，用于后续回归对比
SET @task := 2102603076966547458;
SET @ref := 2102639616300154881;
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000000, 'QA-20260923-000', 'CALIB', 'OK', '原图（基准，应与自身一致）',
       CONCAT('qa-samples/', 'sample-00.png'), CONCAT('qa-samples/', 'sample-00.png'),
       1370, 564, 'CONSISTENT', 'CONSISTENT', 100.00, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-000');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000001, 'QA-20260923-001', 'CALIB', 'OK', '轻微亮度 +4%（正常出图差异，应仍算一致）',
       CONCAT('qa-samples/', 'sample-01.png'), CONCAT('qa-samples/', 'sample-01.png'),
       1370, 564, 'CONSISTENT', 'CONSISTENT', 96.64, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-001');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000002, 'QA-20260923-002', 'CALIB', 'OK', '轻微对比度 +5%（正常出图差异，应仍算一致）',
       CONCAT('qa-samples/', 'sample-02.png'), CONCAT('qa-samples/', 'sample-02.png'),
       1370, 564, 'CONSISTENT', 'CONSISTENT', 97.36, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-002');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000003, 'QA-20260923-003', 'CALIB', 'DEFECT_PRODUCT_TRUTH', '色相偏移 120°（产品颜色被改）',
       CONCAT('qa-samples/', 'sample-03.png'), CONCAT('qa-samples/', 'sample-03.png'),
       1370, 564, 'INCONSISTENT', 'INCONSISTENT', 50.95, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-003');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000004, 'QA-20260923-004', 'CALIB', 'DEFECT_PRODUCT_TRUTH', '去饱和 -70%（产品颜色被改）',
       CONCAT('qa-samples/', 'sample-04.png'), CONCAT('qa-samples/', 'sample-04.png'),
       1370, 564, 'INCONSISTENT', 'UNCERTAIN', 80.84, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-004');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000005, 'QA-20260923-005', 'CALIB', 'DEFECT_PRODUCT_TRUTH', '整图上下翻转（产品形态被改）',
       CONCAT('qa-samples/', 'sample-05.png'), CONCAT('qa-samples/', 'sample-05.png'),
       1370, 564, 'INCONSISTENT', 'INCONSISTENT', 31.21, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-005');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000006, 'QA-20260923-006', 'CALIB', 'DEFECT_ARTIFACT', '右下角加水印文字（画面被污染）',
       CONCAT('qa-samples/', 'sample-06.png'), CONCAT('qa-samples/', 'sample-06.png'),
       1370, 564, 'INCONSISTENT', 'INCONSISTENT', 0.19, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-006');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000007, 'QA-20260923-007', 'CALIB', 'DEFECT_ARTIFACT', '高斯模糊（细节丢失）',
       CONCAT('qa-samples/', 'sample-07.png'), CONCAT('qa-samples/', 'sample-07.png'),
       1370, 564, 'INCONSISTENT', 'UNCERTAIN', 83.32, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-007');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000008, 'QA-20260923-008', 'CALIB', 'DEFECT_ARTIFACT', '叠加噪点（画面劣化）',
       CONCAT('qa-samples/', 'sample-08.png'), CONCAT('qa-samples/', 'sample-08.png'),
       1370, 564, 'INCONSISTENT', 'CONSISTENT', 91.05, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-008');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000009, 'QA-20260923-009', 'CALIB', 'DEFECT_COMPOSITION', '裁掉右侧 25%（构图改变）',
       CONCAT('qa-samples/', 'sample-09.png'), CONCAT('qa-samples/', 'sample-09.png'),
       1027, 564, 'INCONSISTENT', 'UNCERTAIN', NULL, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-009');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000010, 'QA-20260923-010', 'CALIB', 'DEFECT_COMPOSITION', '横向拉伸 1.6 倍（比例失真）',
       CONCAT('qa-samples/', 'sample-10.png'), CONCAT('qa-samples/', 'sample-10.png'),
       2192, 564, 'INCONSISTENT', 'UNCERTAIN', NULL, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-010');
INSERT INTO dp_qa_sample
  (id, sample_no, split, kind, defect_desc, reference_storage_key, candidate_storage_key,
   width, height, ground_truth_verdict, qa_verdict, qa_score, last_run_at,
   create_dept, create_by, create_time, del_flag)
SELECT 2102900000000000011, 'QA-20260923-011', 'CALIB', 'DEFECT_COMPOSITION', '水平镜像（左右关系改变）',
       CONCAT('qa-samples/', 'sample-11.png'), CONCAT('qa-samples/', 'sample-11.png'),
       1370, 564, 'INCONSISTENT', 'INCONSISTENT', 13.48, NOW(),
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dp_qa_sample WHERE sample_no = 'QA-20260923-011');

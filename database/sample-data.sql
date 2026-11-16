-- MedBridge AI - OPTIONAL sample data (100% synthetic, no real patient).
-- Run after schema.sql (or after the application has started once and created the tables).
-- It adds one already-analyzed demo report so the dashboard is not empty.
-- The text below mirrors sample-data/synthetic_discharge_summary.pdf.

USE medbridge_ai;

INSERT INTO reports (file_name, patient_label, status, extracted_text, simplified_summary,
                     diagnosis_as_stated, hospital_stay_summary, uploaded_at, analyzed_at)
VALUES ('synthetic_discharge_summary.pdf', 'Demo - Synthetic patient', 'ANALYZED',
        'SYNTHETIC SAMPLE: Community-acquired pneumonia, right lower lobe. Type 2 diabetes mellitus. Amoxicillin-Clavulanate 625 mg twice daily for 5 more days. Paracetamol 500 mg every 6 hours if needed. Metformin 500 mg twice daily with meals. Pantoprazole 40 mg once daily for 14 days. Vitamin D supplement dose to be confirmed.',
        'The patient came to the hospital with fever, cough and trouble breathing. A chest X-ray showed a lung infection (pneumonia) in the lower right lung. The patient got antibiotics through a vein, then switched to tablets. Oxygen was needed for 2 days, and the patient was breathing normally on room air when leaving. Blood sugar was checked during the stay.',
        'Community-acquired pneumonia, right lower lobe; Type 2 diabetes mellitus (known)',
        'Admitted 2026-11-02, discharged 2026-11-08. IV antibiotics for 3 days, then oral antibiotics. Blood culture results pending at discharge.',
        NOW(6), NOW(6));

SET @rid = LAST_INSERT_ID();

INSERT INTO medications (report_id, name, dosage, frequency, duration, instructions) VALUES
(@rid, 'Amoxicillin-Clavulanate tablet', '625 mg', '1 tablet by mouth twice daily', '5 more days (until 2026-11-13)', 'After meals'),
(@rid, 'Paracetamol tablet', '500 mg', '1 tablet every 6 hours only if needed for fever or pain', NULL, 'Maximum 4 tablets in 24 hours'),
(@rid, 'Metformin tablet', '500 mg', '1 tablet twice daily', NULL, 'With meals. Continue home medicine'),
(@rid, 'Pantoprazole tablet', '40 mg', 'Once daily', '14 days', '30 minutes before breakfast'),
(@rid, 'Vitamin D supplement', NULL, NULL, NULL, 'Dose and schedule to be confirmed at follow-up visit');

INSERT INTO follow_ups (report_id, provider, purpose, appointment_date, details) VALUES
(@rid, 'Primary care physician (Dr. B. Sample)', 'Review recovery and blood sugar', '2026-11-15 at 10:30 AM', 'Bring your blood sugar log'),
(@rid, 'Pulmonology clinic', 'Repeat chest X-ray', 'In 6 weeks (date to be scheduled)', NULL);

INSERT INTO report_precautions (report_id, precaution) VALUES
(@rid, 'Rest at home and drink plenty of fluids.'),
(@rid, 'Finish the full course of the antibiotic.'),
(@rid, 'Follow a diabetic diet. Check blood sugar twice daily and write the readings down.'),
(@rid, 'Do not smoke and avoid smoke exposure.'),
(@rid, 'Return to the emergency department or call your doctor for fever above 101 F (38.3 C) lasting more than 48 hours after discharge, difficulty breathing, chest pain, confusion, or coughing up blood.');

INSERT INTO report_missing_info (report_id, item) VALUES
(@rid, 'Duration is not stated for Paracetamol and Metformin.'),
(@rid, 'Dose and frequency are not stated for the Vitamin D supplement.'),
(@rid, 'The date of the pulmonology appointment has not been scheduled yet.'),
(@rid, 'Blood culture results were pending and no date is given for when they will be shared.');

INSERT INTO checklist_items (report_id, category, description, completed, completed_at) VALUES
(@rid, 'MEDICATION', 'Take Amoxicillin-Clavulanate tablet - 625 mg | 1 tablet by mouth twice daily | for 5 more days (until 2026-11-13) (After meals)', 1, NOW(6)),
(@rid, 'MEDICATION', 'Take Paracetamol tablet - 500 mg | 1 tablet every 6 hours only if needed for fever or pain (Maximum 4 tablets in 24 hours)', 0, NULL),
(@rid, 'MEDICATION', 'Take Metformin tablet - 500 mg | 1 tablet twice daily (With meals. Continue home medicine)', 1, NOW(6)),
(@rid, 'MEDICATION', 'Take Pantoprazole tablet - 40 mg | Once daily | for 14 days (30 minutes before breakfast)', 0, NULL),
(@rid, 'FOLLOW_UP', 'Attend follow-up with Primary care physician (Dr. B. Sample) - 2026-11-15 at 10:30 AM | Review recovery and blood sugar', 0, NULL),
(@rid, 'FOLLOW_UP', 'Attend follow-up with Pulmonology clinic - In 6 weeks (date to be scheduled) | Repeat chest X-ray', 0, NULL),
(@rid, 'PRECAUTION', 'Rest at home and drink plenty of fluids.', 1, NOW(6)),
(@rid, 'PRECAUTION', 'Finish the full course of the antibiotic.', 0, NULL),
(@rid, 'PRECAUTION', 'Do not smoke and avoid smoke exposure.', 0, NULL);

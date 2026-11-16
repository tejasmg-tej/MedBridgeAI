/* MedBridge AI front-end. All server text is escaped before it is inserted into the page. */
const API = '/api';
const $ = (id) => document.getElementById(id);
const esc = (s) => (s === null || s === undefined) ? '' :
    String(s).replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const NOT_SPECIFIED = '<span class="not-specified">Not specified in document</span>';
const val = (s) => (s && String(s).trim()) ? esc(s) : NOT_SPECIFIED;
const fmtDate = (iso) => iso ? new Date(iso).toLocaleString(undefined, {dateStyle: 'medium', timeStyle: 'short'}) : '-';

let currentReportId = null;

/* ------------------------------------------------------------------ API helper */
async function api(path, options = {}) {
    let res;
    try {
        res = await fetch(API + path, options);
    } catch (e) {
        throw new Error('Cannot reach the MedBridge server. Make sure the application is running.');
    }
    if (res.status === 204) return null;
    let data = null;
    try { data = await res.json(); } catch (e) { /* non-JSON body */ }
    if (!res.ok) throw new Error((data && data.message) ? data.message : 'Request failed (HTTP ' + res.status + ').');
    return data;
}

function showAlert(containerId, message, type = 'danger') {
    const box = $(containerId);
    box.className = 'alert alert-' + type + ' alert-dismissible fade show';
    box.innerHTML = '<span>' + esc(message) + '</span><button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>';
}
function clearAlert(containerId) { const box = $(containerId); box.className = 'd-none'; box.innerHTML = ''; }

/* ------------------------------------------------------------------ navigation */
function showView(name) {
    document.querySelectorAll('.view').forEach((v) => v.classList.add('d-none'));
    $('view-' + name).classList.remove('d-none');
    document.querySelectorAll('.mb-navbar .nav-link').forEach((a) => a.classList.toggle('active', a.dataset.view === name));
    clearAlert('globalAlert');
    if (name === 'dashboard') loadDashboard();
    if (name === 'reports') loadReports();
    window.scrollTo({top: 0});
}

document.addEventListener('click', (e) => {
    const nav = e.target.closest('[data-view]');
    if (nav) { e.preventDefault(); showView(nav.dataset.view); }
});

/* ------------------------------------------------------------------ shared renderers */
function statusBadge(status) {
    const map = {ANALYZED: ['success', 'Analyzed'], UPLOADED: ['secondary', 'Not analyzed'], FAILED: ['danger', 'Failed']};
    const [color, label] = map[status] || ['secondary', status];
    return '<span class="badge text-bg-' + color + '">' + esc(label) + '</span>';
}

function progressBar(percent, height = 10) {
    return '<div class="progress" style="height:' + height + 'px;"><div class="progress-bar bg-mb" role="progressbar" style="width:' +
        Number(percent) + '%" aria-valuenow="' + Number(percent) + '" aria-valuemin="0" aria-valuemax="100"></div></div>';
}

function reportsTable(reports, withDelete) {
    if (!reports.length) {
        return '<div class="p-4 text-center text-muted"><i class="bi bi-inbox fs-1 d-block mb-2"></i>No reports yet. Upload a discharge summary to get started.</div>';
    }
    let html = '<div class="table-responsive"><table class="table mb-0"><thead class="table-light"><tr>' +
        '<th>Report</th><th>Uploaded</th><th>Status</th><th class="text-center">Meds</th><th style="min-width:150px">Checklist</th>' +
        (withDelete ? '<th></th>' : '') + '</tr></thead><tbody>';
    for (const r of reports) {
        html += '<tr class="report-row" data-open="' + r.id + '">' +
            '<td><div class="fw-semibold">' + esc(r.patientLabel || r.fileName) + '</div>' +
            (r.patientLabel ? '<div class="small text-muted">' + esc(r.fileName) + '</div>' : '') + '</td>' +
            '<td class="small">' + esc(fmtDate(r.uploadedAt)) + '</td>' +
            '<td>' + statusBadge(r.status) + '</td>' +
            '<td class="text-center">' + r.medicationCount + '</td>' +
            '<td>' + progressBar(r.progressPercent) + '<div class="small text-muted mt-1">' + r.checklistCompleted + '/' + r.checklistTotal + ' done</div></td>' +
            (withDelete ? '<td class="text-end"><button class="btn btn-sm btn-outline-danger" data-delete="' + r.id + '" title="Delete"><i class="bi bi-trash"></i></button></td>' : '') +
            '</tr>';
    }
    return html + '</tbody></table></div>';
}

document.addEventListener('click', async (e) => {
    const del = e.target.closest('[data-delete]');
    if (del) {
        e.stopPropagation();
        if (!confirm('Delete this report and its checklist permanently?')) return;
        try {
            await api('/reports/' + del.dataset.delete, {method: 'DELETE'});
            loadReports();
        } catch (err) { showAlert('globalAlert', err.message); }
        return;
    }
    const row = e.target.closest('[data-open]');
    if (row) openReport(row.dataset.open);
});

/* ------------------------------------------------------------------ dashboard / reports */
async function loadDashboard() {
    try {
        const d = await api('/dashboard');
        $('statReports').textContent = d.totalReports;
        $('statAnalyzed').textContent = d.analyzedReports;
        $('statMeds').textContent = d.totalMedications;
        $('statFailed').textContent = d.failedReports;
        $('overallText').textContent = d.completedChecklistItems + ' of ' + d.totalChecklistItems + ' tasks done';
        const bar = $('overallBar');
        bar.style.width = d.overallProgressPercent + '%';
        bar.textContent = d.overallProgressPercent + '%';
        $('recentReports').innerHTML = reportsTable(d.recentReports, false);
    } catch (err) { showAlert('globalAlert', err.message); }
}

async function loadReports() {
    try {
        $('reportsList').innerHTML = reportsTable(await api('/reports'), true);
    } catch (err) { showAlert('globalAlert', err.message); }
}

/* ------------------------------------------------------------------ upload workflow */
$('uploadForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAlert('uploadAlert');
    const file = $('fileInput').files[0];
    if (!file) return showAlert('uploadAlert', 'Please choose a PDF file first.');
    if (!file.name.toLowerCase().endsWith('.pdf')) return showAlert('uploadAlert', 'Only PDF files are supported.');
    if (file.size > 10 * 1024 * 1024) return showAlert('uploadAlert', 'The file is larger than 10 MB.');

    setUploadBusy(true, 'Uploading and extracting text from your PDF...');
    let uploaded = null;
    try {
        const form = new FormData();
        form.append('file', file);
        const label = $('patientLabel').value.trim();
        if (label) form.append('patientLabel', label);
        uploaded = await api('/reports/upload', {method: 'POST', body: form});

        setUploadBusy(true, 'Analyzing with Gemini AI. This can take up to a minute...');
        const analyzed = await api('/reports/' + uploaded.id + '/analyze', {method: 'POST'});
        $('uploadForm').reset();
        renderDetail(analyzed);
        showView('detail');
    } catch (err) {
        let msg = err.message;
        if (uploaded) msg += ' Your PDF was saved. You can retry the analysis from the report page.';
        showAlert('uploadAlert', msg);
        if (uploaded) {
            $('uploadAlert').insertAdjacentHTML('beforeend',
                '<div class="mt-2"><button type="button" class="btn btn-sm btn-outline-primary" id="openSavedBtn">Open saved report</button></div>');
            $('openSavedBtn').addEventListener('click', () => openReport(uploaded.id));
        }
    } finally {
        setUploadBusy(false);
    }
});

function setUploadBusy(busy, message) {
    $('uploadBtn').disabled = busy;
    $('uploadProgress').classList.toggle('d-none', !busy);
    if (message) $('uploadStep').textContent = message;
}

/* ------------------------------------------------------------------ report detail */
async function openReport(id) {
    try {
        renderDetail(await api('/reports/' + id));
        showView('detail');
    } catch (err) { showAlert('globalAlert', err.message); }
}

function renderDetail(r) {
    currentReportId = r.id;
    const analyzed = r.status === 'ANALYZED';
    let html = '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">' +
        '<div><button class="btn btn-sm btn-outline-secondary me-2 no-print" data-view="reports"><i class="bi bi-arrow-left"></i> Reports</button>' +
        '<span class="h4 align-middle text-mb">' + esc(r.patientLabel || r.fileName) + '</span> ' + statusBadge(r.status) + '</div>' +
        '<div class="no-print">' +
        (analyzed ? '<button class="btn btn-sm btn-outline-primary me-2" onclick="window.print()"><i class="bi bi-printer"></i> Print</button>' : '') +
        '<button class="btn btn-sm btn-mb" id="analyzeBtn"><i class="bi bi-arrow-repeat"></i> ' + (analyzed ? 'Re-analyze' : 'Analyze now') + '</button></div></div>' +
        '<div class="small text-muted mb-3">File: ' + esc(r.fileName) + ' &middot; Uploaded ' + esc(fmtDate(r.uploadedAt)) +
        (r.analyzedAt ? ' &middot; Analyzed ' + esc(fmtDate(r.analyzedAt)) : '') + '</div>' +
        '<div id="detailAlert" class="d-none"></div>';

    if (r.errorMessage && !analyzed) {
        html += '<div class="alert alert-danger"><strong>Analysis could not be completed.</strong><br>' + esc(r.errorMessage) + '</div>';
    }
    if (!analyzed && !r.errorMessage) {
        html += '<div class="alert alert-info">This report has not been analyzed yet. Click <strong>Analyze now</strong>.</div>';
    }

    if (analyzed) {
        html += '<div class="card shadow-sm mb-4"><div class="card-body"><h2 class="section-title"><i class="bi bi-chat-heart me-2"></i>Your stay in simple words</h2>' +
            '<div class="summary-box">' + val(r.simplifiedSummary) + '</div>' +
            '<div class="row mt-3 g-3"><div class="col-md-6"><div class="fw-semibold">Diagnosis (as written in the document)</div><div>' + val(r.diagnosisAsStated) + '</div></div>' +
            '<div class="col-md-6"><div class="fw-semibold">Hospital stay</div><div>' + val(r.hospitalStaySummary) + '</div></div></div></div></div>';

        // medications
        html += '<div class="card shadow-sm mb-4"><div class="card-body"><h2 class="section-title"><i class="bi bi-capsule me-2"></i>Medications</h2>';
        if (r.medications.length) {
            html += '<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead class="table-light"><tr><th>Medicine</th><th>Dosage</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr></thead><tbody>';
            for (const m of r.medications) {
                html += '<tr><td class="fw-semibold">' + esc(m.name) + '</td><td>' + val(m.dosage) + '</td><td>' + val(m.frequency) +
                    '</td><td>' + val(m.duration) + '</td><td>' + val(m.instructions) + '</td></tr>';
            }
            html += '</tbody></table></div>';
        } else {
            html += '<div class="text-muted">No medications were found in this document.</div>';
        }
        html += '</div></div>';

        // follow-ups
        html += '<div class="card shadow-sm mb-4"><div class="card-body"><h2 class="section-title"><i class="bi bi-calendar-check me-2"></i>Follow-up appointments</h2>';
        if (r.followUps.length) {
            html += '<div class="row g-3">';
            for (const f of r.followUps) {
                html += '<div class="col-md-6"><div class="border rounded-3 p-3 h-100"><div class="fw-semibold">' + val(f.provider) + '</div>' +
                    '<div><i class="bi bi-clock me-1"></i>' + val(f.appointmentDate) + '</div>' +
                    '<div class="small mt-1"><strong>Purpose:</strong> ' + val(f.purpose) + '</div>' +
                    (f.details ? '<div class="small text-muted mt-1">' + esc(f.details) + '</div>' : '') + '</div></div>';
            }
            html += '</div>';
        } else {
            html += '<div class="text-muted">No follow-up appointments were found in this document.</div>';
        }
        html += '</div></div>';

        // precautions
        html += '<div class="card shadow-sm mb-4"><div class="card-body"><h2 class="section-title"><i class="bi bi-shield-check me-2"></i>Precautions &amp; instructions</h2>';
        html += r.precautions.length
            ? '<ul class="mb-0">' + r.precautions.map((p) => '<li class="mb-1">' + esc(p) + '</li>').join('') + '</ul>'
            : '<div class="text-muted">No precautions were found in this document.</div>';
        html += '</div></div>';

        // missing info
        if (r.missingInformation.length) {
            html += '<div class="missing-box mb-4"><h2 class="section-title text-warning-emphasis"><i class="bi bi-exclamation-triangle me-2"></i>Information missing or unclear in your document</h2>' +
                '<ul class="mb-1">' + r.missingInformation.map((m) => '<li>' + esc(m) + '</li>').join('') + '</ul>' +
                '<div class="small">Please ask your doctor, nurse, or pharmacist about these items.</div></div>';
        }

        // checklist
        html += '<div class="card shadow-sm mb-4"><div class="card-body"><div class="d-flex justify-content-between align-items-center mb-2">' +
            '<h2 class="section-title mb-0"><i class="bi bi-list-check me-2"></i>Your care checklist</h2>' +
            '<span class="small text-muted" id="checkCount">' + r.checklistCompleted + '/' + r.checklistTotal + ' done</span></div>' +
            '<div id="checkProgress" class="mb-3">' + progressBar(r.progressPercent, 14) + '</div>';
        if (r.checklist.length) {
            const groups = [['MEDICATION', 'Medications'], ['FOLLOW_UP', 'Appointments'], ['PRECAUTION', 'Precautions']];
            for (const [cat, title] of groups) {
                const items = r.checklist.filter((c) => c.category === cat);
                if (!items.length) continue;
                html += '<div class="fw-semibold small text-uppercase text-muted mt-3 mb-2">' + title + '</div>';
                for (const c of items) {
                    html += '<div class="check-item' + (c.completed ? ' done' : '') + '" id="item-' + c.id + '">' +
                        '<input type="checkbox" id="chk-' + c.id + '" data-check="' + c.id + '"' + (c.completed ? ' checked' : '') + '>' +
                        '<label for="chk-' + c.id + '">' + esc(c.description) + '</label></div>';
                }
            }
        } else {
            html += '<div class="text-muted">No checklist items could be created from this document.</div>';
        }
        html += '</div></div>';
    }

    html += '<div class="alert alert-warning small"><i class="bi bi-shield-exclamation me-1"></i><strong>Medical disclaimer.</strong> ' + esc(r.disclaimer) + '</div>';
    $('detailContent').innerHTML = html;
    $('analyzeBtn').addEventListener('click', () => runAnalysis(r.id, analyzed));
}

async function runAnalysis(id, alreadyAnalyzed) {
    if (alreadyAnalyzed && !confirm('Re-analyzing replaces the current results and resets your checklist progress. Continue?')) return;
    const btn = $('analyzeBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Analyzing...';
    try {
        renderDetail(await api('/reports/' + id + '/analyze', {method: 'POST'}));
    } catch (err) {
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-arrow-repeat"></i> ' + (alreadyAnalyzed ? 'Re-analyze' : 'Retry analysis');
        showAlert('detailAlert', err.message);
    }
}

/* ------------------------------------------------------------------ checklist toggling */
document.addEventListener('change', async (e) => {
    const box = e.target.closest('[data-check]');
    if (!box) return;
    const wanted = box.checked;
    try {
        const res = await api('/checklist/' + box.dataset.check, {
            method: 'PATCH',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({completed: wanted})
        });
        $('item-' + res.item.id).classList.toggle('done', res.item.completed);
        $('checkCount').textContent = res.checklistCompleted + '/' + res.checklistTotal + ' done';
        $('checkProgress').innerHTML = progressBar(res.progressPercent, 14);
    } catch (err) {
        box.checked = !wanted;
        showAlert('detailAlert', err.message);
    }
});

/* ------------------------------------------------------------------ start */
showView('dashboard');

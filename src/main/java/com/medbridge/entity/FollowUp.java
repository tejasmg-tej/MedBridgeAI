package com.medbridge.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "follow_ups")
public class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "provider", length = 200)
    private String provider;

    @Column(name = "purpose", length = 500)
    private String purpose;

    /** Stored exactly as written in the document (may be a date, "in 1 week", etc.). */
    @Column(name = "appointment_date", length = 100)
    private String appointmentDate;

    @Column(name = "details", length = 1000)
    private String details;

    public Long getId() { return id; }
    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(String appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}

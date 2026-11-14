package com.dao;

import java.math.BigDecimal;
import java.util.Date;
import javax.persistence.*;

@Entity
@Table(name = "CompOffBalanceDetails")
public class CompOffBalanceDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer COBDID;

    private Integer staffID;

    private BigDecimal allocated;

    private BigDecimal used;

    private BigDecimal balance;

    private String status;

    @Column(insertable = false, updatable = false)
    private Date createdDate;

    @Column(insertable = false, updatable = false)
    private Date updatedDate;

    public Integer getCOBDID() { return COBDID; }
    public void setCOBDID(Integer COBDID) { this.COBDID = COBDID; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public BigDecimal getAllocated() { return allocated; }
    public void setAllocated(BigDecimal allocated) { this.allocated = allocated; }

    public BigDecimal getUsed() { return used; }
    public void setUsed(BigDecimal used) { this.used = used; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public Date getUpdatedDate() { return updatedDate; }
}

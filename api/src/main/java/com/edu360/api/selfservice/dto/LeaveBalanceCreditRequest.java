package com.edu360.api.selfservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceCreditRequest(UUID leaveTypeId, short leaveYear, BigDecimal days) {}

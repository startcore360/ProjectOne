package com.edu360.api.institution;

import java.util.List;

public record TeacherLookupResponse(
        List<TeacherLookupItem> items,
        StaffListResponse.PageInfo page
) {}

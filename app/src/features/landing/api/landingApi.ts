import api from "../../../services/axios";

export interface PublicInstitutionItem {
  name: string;
  slug: string;
  tenantType: string;
}

export interface PublicCatalogModuleItem {
  moduleCode: string;
  itemName: string;
  description: string | null;
}

export interface LandingTestimonialItem {
  testimonialId: string;
  kind: "TEXT" | "VIDEO";
  authorName: string;
  authorRole: string;
  institutionName: string;
  quoteText: string;
  videoUrl: string | null;
  videoMediaId: string | null;
  posterMediaId: string | null;
  displayOrder: number;
  published: boolean;
  rowVersion: number;
}

export interface PublicLandingData {
  heroEyebrow: string;
  heroTitle: string;
  heroSubtitle: string;
  heroNote: string;
  trustLabel: string;
  whyChooseJson: string;
  roleBenefitsJson: string;
  solutionsJson: string;
  stepsJson: string;
  contactEmail: string;
  footerTagline: string;
  heroLightMediaId: string | null;
  heroDarkMediaId: string | null;
  showcaseLightMediaId: string | null;
  showcaseDarkMediaId: string | null;
  testimonials: LandingTestimonialItem[];
  catalogModules: PublicCatalogModuleItem[];
}

export interface PublicEnquiryPayload {
  enquiryType: "QUOTATION" | "DEMO";
  institutionType?: string;
  studentRange?: string;
  collegesCount?: number;
  schoolsCount?: number;
  studentsEstimate?: number;
  staffEstimate?: number;
  billingCycleMonths?: number;
  includeSetupFee?: boolean;
  selectedModules?: string[];
  migrationSupport?: string;
  trainingMode?: string;
  contactName: string;
  contactEmail: string;
  contactPhone?: string;
  institutionName: string;
  notes?: string;
}

export interface PublicEnquiryReceipt {
  enquiryId: string;
  enquiryType: "QUOTATION" | "DEMO";
  status: string;
  message: string;
  submittedAt: string;
}

export interface PlatformLandingConfigDto {
  configId: string;
  heroEyebrow: string;
  heroTitle: string;
  heroSubtitle: string;
  heroNote: string;
  trustLabel: string;
  whyChooseJson: string;
  roleBenefitsJson: string;
  solutionsJson: string;
  stepsJson: string;
  contactEmail: string;
  footerTagline: string;
  heroLightMediaId: string | null;
  heroDarkMediaId: string | null;
  showcaseLightMediaId: string | null;
  showcaseDarkMediaId: string | null;
  rowVersion: number;
}

export interface DirectoryTenantItem {
  tenantId: string;
  tenantName: string;
  subdomain: string;
  tenantType: string;
  state: string;
  listedInDirectory: boolean;
}

export interface PlatformLandingCmsData {
  config: PlatformLandingConfigDto;
  testimonials: LandingTestimonialItem[];
  directoryTenants: DirectoryTenantItem[];
}

export interface PlatformLandingEnquiryItem {
  enquiryId: string;
  enquiryType: "QUOTATION" | "DEMO";
  institutionType: string | null;
  studentRange: string | null;
  collegesCount: number;
  schoolsCount: number;
  studentsEstimate: number;
  staffEstimate: number;
  billingCycleMonths: number;
  includeSetupFee: boolean;
  selectedModules: string;
  migrationSupport: string | null;
  trainingMode: string | null;
  contactName: string;
  contactEmail: string;
  contactPhone: string | null;
  institutionName: string;
  notes: string | null;
  quoteSnapshotJson: string | null;
  status: "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED";
  internalNotes: string | null;
  handledBy: string | null;
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface LandingMediaUploadResult {
  mediaId: string;
  mediaKind: "IMAGE" | "VIDEO";
  fileName: string;
  contentType: string;
  byteSize: number;
  scanStatus: string;
  publicUrl: string;
}

export function publicMediaUrl(mediaId: string | null | undefined): string | null {
  if (!mediaId) return null;
  const base = (api.defaults.baseURL || "/api").replace(/\/$/, "");
  return `${base}/public/landing/media/${mediaId}`;
}

export const landingApi = {
  async listPublicInstitutions(query = ""): Promise<PublicInstitutionItem[]> {
    const { data } = await api.get<PublicInstitutionItem[]>("/public/institutions", {
      params: query ? { q: query } : undefined,
    });
    return data;
  },

  async getPublicLanding(): Promise<PublicLandingData> {
    const { data } = await api.get<PublicLandingData>("/public/landing");
    return data;
  },

  async submitPublicEnquiry(payload: PublicEnquiryPayload): Promise<PublicEnquiryReceipt> {
    const { data } = await api.post<PublicEnquiryReceipt>("/public/enquiries", payload);
    return data;
  },

  async getCmsOverview(): Promise<PlatformLandingCmsData> {
    const { data } = await api.get<PlatformLandingCmsData>("/platform/landing");
    return data;
  },

  async updateConfig(
    payload: Omit<PlatformLandingConfigDto, "configId" | "rowVersion">,
    rowVersion: number,
  ): Promise<PlatformLandingConfigDto> {
    const { data } = await api.put<PlatformLandingConfigDto>("/platform/landing/config", payload, {
      headers: { "If-Match": `"${rowVersion}"` },
    });
    return data;
  },

  async createTestimonial(
    payload: Omit<LandingTestimonialItem, "testimonialId" | "rowVersion">,
  ): Promise<LandingTestimonialItem> {
    const { data } = await api.post<LandingTestimonialItem>("/platform/landing/testimonials", payload);
    return data;
  },

  async updateTestimonial(
    testimonialId: string,
    payload: Omit<LandingTestimonialItem, "testimonialId" | "rowVersion">,
    rowVersion: number,
  ): Promise<LandingTestimonialItem> {
    const { data } = await api.put<LandingTestimonialItem>(
      `/platform/landing/testimonials/${testimonialId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } },
    );
    return data;
  },

  async deleteTestimonial(testimonialId: string): Promise<void> {
    await api.delete(`/platform/landing/testimonials/${testimonialId}`);
  },

  async reorderTestimonials(orderedIds: string[]): Promise<LandingTestimonialItem[]> {
    const { data } = await api.post<LandingTestimonialItem[]>("/platform/landing/testimonials/reorder", {
      orderedIds,
    });
    return data;
  },

  async uploadMedia(file: File, mediaKind: "IMAGE" | "VIDEO"): Promise<LandingMediaUploadResult> {
    const form = new FormData();
    form.append("file", file);
    form.append("mediaKind", mediaKind);
    const { data } = await api.post<LandingMediaUploadResult>("/platform/landing/media", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  },

  async toggleDirectory(tenantId: string, listedInDirectory: boolean): Promise<DirectoryTenantItem> {
    const { data } = await api.patch<DirectoryTenantItem>(`/platform/landing/directory/${tenantId}`, {
      listedInDirectory,
    });
    return data;
  },

  async listEnquiries(type?: string, status?: string): Promise<PlatformLandingEnquiryItem[]> {
    const { data } = await api.get<PlatformLandingEnquiryItem[]>("/platform/landing/enquiries", {
      params: {
        ...(type ? { type } : {}),
        ...(status ? { status } : {}),
      },
    });
    return data;
  },

  async updateEnquiryStatus(
    enquiryId: string,
    status: "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED",
    internalNotes: string,
    rowVersion: number,
  ): Promise<PlatformLandingEnquiryItem> {
    const { data } = await api.patch<PlatformLandingEnquiryItem>(
      `/platform/landing/enquiries/${enquiryId}`,
      { status, internalNotes },
      { headers: { "If-Match": `"${rowVersion}"` } },
    );
    return data;
  },
};

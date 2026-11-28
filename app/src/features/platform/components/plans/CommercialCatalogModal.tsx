import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import {
  platformTenantService,
  type CommercialCatalogItemDto,
} from "../../api/platformTenantService";

type CatalogDraft = {
  itemCode: string;
  itemName: string;
  category: string;
  unitCode: string;
  pricingBasis: string;
  unitAmount: number;
  unitsPerQuantity: number;
  billingIntervalMonths: number;
  moduleCode: string;
  description: string;
  available: boolean;
};

const emptyDraft: CatalogDraft = {
  itemCode: "",
  itemName: "",
  category: "CAPACITY",
  unitCode: "STUDENT_PACK",
  pricingBasis: "RECURRING_FIXED",
  unitAmount: 0,
  unitsPerQuantity: 1,
  billingIntervalMonths: 1,
  moduleCode: "",
  description: "",
  available: true,
};

export interface CommercialCatalogModalProps {
  isOpen: boolean;
  item: CommercialCatalogItemDto | null;
  onClose: () => void;
  onSaved: () => void;
}

export function CommercialCatalogModal({ isOpen, item, onClose, onSaved }: CommercialCatalogModalProps) {
  const [draft, setDraft] = useState<CatalogDraft>(emptyDraft);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isOpen) return;
    setError("");
    setDraft(item ? {
      itemCode: item.itemCode,
      itemName: item.itemName,
      category: item.category,
      unitCode: item.unitCode,
      pricingBasis: item.pricingBasis,
      unitAmount: item.unitAmount,
      unitsPerQuantity: item.unitsPerQuantity,
      billingIntervalMonths: item.billingIntervalMonths ?? 1,
      moduleCode: item.moduleCode ?? "",
      description: item.description ?? "",
      available: item.available,
    } : emptyDraft);
  }, [isOpen, item]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    const payload = {
      ...draft,
      itemCode: draft.itemCode.trim().toUpperCase(),
      itemName: draft.itemName.trim(),
      moduleCode: draft.category === "MODULE" ? draft.moduleCode.trim().toUpperCase() : null,
      description: draft.description.trim() || null,
    };
    try {
      if (item) {
        await platformTenantService.updateCommercialCatalogItem(item.catalogItemId, payload, item.rowVersion);
      } else {
        await platformTenantService.createCommercialCatalogItem(payload);
      }
      onSaved();
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to save the commercial catalog item.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} kicker="COMMERCIAL CATALOG"
      title={item ? "Edit catalog item" : "Add catalog item"} titleId="catalog-item-title" size="lg"
      description="Catalog items define reusable module, capacity and service pricing for plans and customer quotes.">
      <form onSubmit={submit}>
        {item && <Notice tone="info">Catalog identity fields are locked because plans and invoice history may reference this item.</Notice>}
        <div className="app-form-grid">
          <TextField id="catalog-item-code" label="Item code" required value={draft.itemCode}
            disabled={Boolean(item)}
            onChange={(event) => setDraft((current) => ({ ...current, itemCode: event.target.value }))} />
          <TextField id="catalog-item-name" label="Item name" required value={draft.itemName}
            onChange={(event) => setDraft((current) => ({ ...current, itemName: event.target.value }))} />
          <SelectField id="catalog-category" label="Category" value={draft.category} disabled={Boolean(item)}
            onChange={(event) => {
              const category = event.target.value;
              setDraft((current) => ({
                ...current,
                category,
                unitCode: category === "MODULE" ? "MODULE" : category === "SERVICE" ? "SERVICE" : "STUDENT_PACK",
                pricingBasis: category === "CAPACITY" ? "RECURRING_FIXED"
                  : category === "MODULE" ? "PER_MODULE_YEAR" : "RECURRING_FIXED",
                moduleCode: category === "MODULE" ? current.moduleCode : "",
              }));
            }}
            options={[
              { value: "CAPACITY", label: "Capacity pack" },
              { value: "MODULE", label: "Product module" },
              { value: "SERVICE", label: "Service add-on" },
            ]} />
          <SelectField id="catalog-unit-code" label="Capacity / unit" value={draft.unitCode} disabled={Boolean(item)}
            onChange={(event) => setDraft((current) => ({ ...current, unitCode: event.target.value }))}
            options={draft.category === "CAPACITY" ? [
              { value: "INSTITUTION", label: "Institution" },
              { value: "COLLEGE", label: "College" },
              { value: "SCHOOL", label: "School" },
              { value: "STUDENT_PACK", label: "Student pack" },
              { value: "STAFF_PACK", label: "Staff pack" },
            ] : [{ value: draft.category, label: draft.category === "MODULE" ? "Module" : "Service" }]} />
          {draft.category === "MODULE" && <TextField id="catalog-module-code" label="Module code" required value={draft.moduleCode}
            disabled={Boolean(item)}
            onChange={(event) => setDraft((current) => ({ ...current, moduleCode: event.target.value }))} />}
          <SelectField id="catalog-pricing-basis" label="Pricing basis" value={draft.pricingBasis}
            onChange={(event) => setDraft((current) => ({ ...current, pricingBasis: event.target.value }))}
            options={[
              { value: "ONE_TIME", label: "One-time" },
              { value: "RECURRING_FIXED", label: "Recurring fixed" },
              { value: "PER_UNIT", label: "Per unit" },
              { value: "PER_STUDENT_YEAR", label: "Per student-year" },
              { value: "PER_MODULE_YEAR", label: "Per module-year" },
            ].filter((option) => draft.category !== "CAPACITY"
              || ["ONE_TIME", "RECURRING_FIXED", "PER_UNIT"].includes(option.value))} />
          <TextField id="catalog-unit-amount" label="Price per unit (₹)" type="number" min="0" step="0.01"
            value={String(draft.unitAmount)}
            onChange={(event) => setDraft((current) => ({ ...current, unitAmount: Math.max(0, Number(event.target.value) || 0) }))} />
          <TextField id="catalog-units-per-quantity" label="Capacity per package" type="number" min="1"
            disabled={Boolean(item)}
            value={String(draft.unitsPerQuantity)}
            onChange={(event) => setDraft((current) => ({ ...current, unitsPerQuantity: Math.max(1, Number(event.target.value) || 1) }))} />
          <TextField id="catalog-billing-interval" label="Billing interval (months)" type="number" min="1" max="36"
            value={String(draft.billingIntervalMonths)}
            onChange={(event) => setDraft((current) => ({ ...current, billingIntervalMonths: Math.min(36, Math.max(1, Number(event.target.value) || 1)) }))} />
        </div>
        <TextField id="catalog-description" label="Description" value={draft.description}
          onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} />
        <label className="app-customer-quote__setup">
          <input type="checkbox" checked={draft.available}
            onChange={(event) => setDraft((current) => ({ ...current, available: event.target.checked }))} />
          Available for new plans and quotes
        </label>
        {error && <Notice tone="danger">{error}</Notice>}
        <footer>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={saving}>{item ? "Save catalog item" : "Create catalog item"}</Button>
        </footer>
      </form>
    </Modal>
  );
}

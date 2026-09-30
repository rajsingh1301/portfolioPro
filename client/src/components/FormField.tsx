interface FormFieldProps {
  id: string
  label: string
  type: string
  value: string
  autoComplete: string
  onChange: (value: string) => void
  hint?: string
}

export function FormField({ id, label, type, value, autoComplete, onChange, hint }: FormFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        required
        aria-describedby={hint === undefined ? undefined : `${id}-hint`}
        onChange={(event) => onChange(event.target.value)}
        className="input mt-1"
      />
      {hint !== undefined && (
        <p id={`${id}-hint`} className="mt-1 text-xs text-ink-3">
          {hint}
        </p>
      )}
    </div>
  )
}

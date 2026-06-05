export interface Alert {
  title: string
  description: string
}

const alerts = ref<Alert[]>([])

export function addAlert(alert: Alert) {
  alerts.value.push(alert)
}

export function useAlerts(): Ref<Alert[]> {
  return alerts
}

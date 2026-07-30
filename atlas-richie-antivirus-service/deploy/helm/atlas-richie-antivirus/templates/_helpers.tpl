{{- define "atlas-richie-antivirus.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "atlas-richie-antivirus.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "atlas-richie-antivirus.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "atlas-richie-antivirus.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
app.kubernetes.io/name: {{ include "atlas-richie-antivirus.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "atlas-richie-antivirus.selectorLabels" -}}
app.kubernetes.io/name: {{ include "atlas-richie-antivirus.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

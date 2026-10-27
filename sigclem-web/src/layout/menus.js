import {
  Award, BarChart3, Bell, CalendarDays, ClipboardCheck, ClipboardList, CreditCard, Eye, FileText, Home,
  IdCard, KeyRound, Languages, Layers, Mail, MailOpen, NotebookPen, PenLine, Receipt, ShieldCheck, UserRound, UserSquare, Users, LayoutDashboard, CalendarRange,
} from 'lucide-react';

/** Menú lateral de cada rol, conforme a la matriz de permisos (Tabla 59) y los mockups (Figuras 37 a 45). */
export const MENUS = {
  ALUMNO: {
    seccion: 'Alumno',
    elementos: [
      { a: '/alumno', etiqueta: 'Mi expediente', icono: IdCard, fin: true },
      { a: '/alumno/cursos', etiqueta: 'Cursos disponibles', icono: CalendarRange },
      { a: '/alumno/examenes', etiqueta: 'Exámenes', icono: ClipboardList },
      { a: '/alumno/diagnostico', etiqueta: 'Evaluación de diagnóstico', icono: ClipboardCheck },
      { a: '/alumno/pagos', etiqueta: 'Pagos', icono: CreditCard },
      { a: '/alumno/boletas', etiqueta: 'Boletas', icono: FileText },
      { a: '/alumno/constancias', etiqueta: 'Constancias', icono: Award },
      { a: '/alumno/seguridad', etiqueta: 'Mi contraseña', icono: KeyRound },
      // Solo para quien respondió en su registro que habla una lengua materna.
      { a: '/alumno/lengua-materna', etiqueta: 'Lengua materna', icono: Languages, requiereLenguaMaterna: true },
      { a: '/alumno/mensajes', etiqueta: 'Mensajes', icono: Mail, seccion: 'Comunicación' },
    ],
  },
  FACILITADOR: {
    seccion: 'Facilitador',
    elementos: [
      { a: '/facilitador', etiqueta: 'Inicio', icono: Home, fin: true },
      { a: '/facilitador/grupos', etiqueta: 'Mis grupos', icono: Layers },
      { a: '/facilitador/actas', etiqueta: 'Actas de calificaciones', icono: NotebookPen },
      { a: '/facilitador/perfil', etiqueta: 'Mi perfil', icono: UserRound },
      { a: '/facilitador/mensajes', etiqueta: 'Mensajes', icono: Mail, seccion: 'Comunicación' },
    ],
  },
  COORDINADOR: {
    seccion: 'Coordinación',
    elementos: [
      { a: '/coordinacion', etiqueta: 'Panel', icono: LayoutDashboard, fin: true },
      { a: '/coordinacion/alumnos', etiqueta: 'Alumnos', icono: Users },
      { a: '/coordinacion/diagnosticos', etiqueta: 'Exámenes de colocación', icono: ClipboardList },
      { a: '/coordinacion/evaluaciones-diagnosticas', etiqueta: 'Evaluaciones diagnósticas', icono: ClipboardCheck },
      { a: '/coordinacion/facilitadores', etiqueta: 'Facilitadores', icono: UserSquare },
      { a: '/coordinacion/grupos', etiqueta: 'Grupos y horarios', icono: CalendarDays },
      { a: '/coordinacion/comprobantes', etiqueta: 'Pagos y tarifas', icono: Receipt },
      { a: '/coordinacion/constancias', etiqueta: 'Constancias', icono: Award },
      { a: '/coordinacion/firmas-membrete', etiqueta: 'Firmas y membrete', icono: PenLine },
      { a: '/coordinacion/cuentas', etiqueta: 'Cuentas y contraseñas', icono: KeyRound },
      { a: '/coordinacion/lenguas-maternas', etiqueta: 'Lenguas maternas', icono: Languages },
      { a: '/coordinacion/reportes', etiqueta: 'Reportes', icono: BarChart3 },
      { a: '/coordinacion/mensajes', etiqueta: 'Mensajes', icono: Mail, seccion: 'Comunicación' },
      { a: '/coordinacion/seguridad', etiqueta: 'Mi contraseña', icono: KeyRound, seccion: 'Cuenta' },
    ],
  },
  JEFA_DGTV: {
    seccion: 'Supervisión',
    elementos: [
      { a: '/supervision', etiqueta: 'Panorama general', icono: Eye, fin: true },
      { a: '/supervision/alumnos', etiqueta: 'Alumnos', icono: Users },
      { a: '/supervision/facilitadores', etiqueta: 'Facilitadores', icono: UserSquare },
      { a: '/supervision/grupos', etiqueta: 'Grupos y horarios', icono: CalendarDays },
      { a: '/supervision/examenes', etiqueta: 'Exámenes de colocación', icono: ClipboardList },
      { a: '/supervision/diagnosticos', etiqueta: 'Evaluaciones diagnósticas', icono: ClipboardCheck },
      { a: '/supervision/pagos', etiqueta: 'Pagos y comprobantes', icono: Receipt },
      { a: '/supervision/constancias', etiqueta: 'Constancias', icono: Award },
      { a: '/supervision/lenguas-maternas', etiqueta: 'Lenguas maternas', icono: Languages },
      { a: '/supervision/reportes', etiqueta: 'Reportes', icono: BarChart3 },
      { a: '/supervision/mensajes', etiqueta: 'Mensajes', icono: MailOpen },
      { a: '/supervision/seguridad', etiqueta: 'Mi contraseña', icono: KeyRound, seccion: 'Cuenta' },
    ],
  },
};

// El administrador ve el menú del coordinador (hace todo lo del coordinador) más la bitácora de auditoría.
MENUS.ADMIN = {
  seccion: 'Administración',
  elementos: [
    ...MENUS.COORDINADOR.elementos,
    { a: '/coordinacion/auditoria', etiqueta: 'Auditoría', icono: ShieldCheck, seccion: 'Administración' },
  ],
};

export const IconoNotificaciones = Bell;

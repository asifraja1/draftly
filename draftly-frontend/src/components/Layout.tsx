import { NavLink, Outlet } from 'react-router-dom'
import { Mail, BookOpen, FileText, ScrollText, LogOut } from 'lucide-react'
import { useSse } from '../hooks/useSse'

const navItems = [
  { to: '/train', icon: BookOpen, label: 'Train' },
  { to: '/drafts', icon: FileText, label: 'Drafts' },
  { to: '/summaries', icon: ScrollText, label: 'Summaries' },
]

export default function Layout() {
  useSse()
  return (
    <div className="min-h-screen bg-gray-950 flex">
      {/* Sidebar */}
      <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col py-6 px-3 flex-shrink-0">
        {/* Logo */}
        <div className="flex items-center gap-2 px-3 mb-8">
          <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center">
            <Mail className="w-4 h-4 text-white" />
          </div>
          <span className="font-bold text-white">Draftly</span>
        </div>

        {/* Nav */}
        <nav className="flex-1 space-y-1">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors ${
                  isActive
                    ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-600/30'
                    : 'text-gray-400 hover:text-white hover:bg-gray-800'
                }`
              }
            >
              <Icon className="w-4 h-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Logout */}
        <a
          href="/logout"
          className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-gray-500 hover:text-white hover:bg-gray-800 transition-colors"
        >
          <LogOut className="w-4 h-4" /> Logout
        </a>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}

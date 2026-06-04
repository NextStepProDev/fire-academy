<div align="center">

# 🔥 Fire Academy

**A training club's digital home — with live online enrollment.**

Personal training and small-group sessions, sports camps, and courses — from martial arts to coaching certifications. For beginners and advanced alike.

[`fireworkout.pl`](https://fireworkout.pl) · FIZJO4LIFE Sp. z o.o. · Polish-language application

</div>

---

## What Fire Academy is

Fire Academy is a training club's complete online presence: a public site that showcases the offer **and** an enrollment system that actually handles participant sign-ups and email communication — no spreadsheets, no manual coordination, no third-party forms.

A single application plays two roles:

- **Customer-facing site** — browse the offer, see session details, get to know the staff, and sign up for a specific date in seconds.
- **Organizer panel** — manage the entire offer and all enrollments from one place, with automatic participant notifications.

This is not a static landing page. Every date has its own schedule, price, and roster of registered participants, and the system handles enrollment rules, deadlines, and data-privacy compliance on its own.

## Who it's for

| Audience | What they get |
|----------|---------------|
| **Participant** | A quick overview of the offer and self-service online sign-up with an email confirmation. |
| **Organizer / coach** | Full control over the offer, staff, and enrollments from the admin panel. |
| **The club** | A professional presence in Google results and clean previews when shared on social media. |

## The offer — three pillars

The whole application is organized around three session categories, each with its own page, staff, and schedule:

- 🥊 **Trainings** — personal and small-group sessions.
- 🏕️ **Camps** — residential sports camps.
- 🎓 **Courses** — including coaching certifications.

Within each category the offer is split into two levels:

- **Types** — the "what" we offer (a given kind of session), with a description and a photo gallery.
- **Dates** — the concrete "when and for how much": date, hours, location, price, and open enrollment.

## How it works

### The participant's path

1. Opens the site and picks a category (Trainings / Camps / Courses).
2. Browses the **types** of sessions (gallery, description) and the upcoming **dates**.
3. For each date, sees the price and location at a glance.
4. Fills in a short **enrollment form** (first name, last name, email, phone, optional note for the organizer).
5. Receives a **confirmation email**, while the organizer gets a notification about the new sign-up.

The system enforces the rules on its own: it prevents signing up twice for the same date and **automatically closes enrollment 24 hours before the start** (directing people to contact the club directly).

### The organizer's path

The admin panel (hidden, available after logging in) offers tabs to manage everything:

- **Staff** — instructors with a photo, bio, and assignment to categories.
- **Trainings / Camps / Courses** — types and dates: creation, editing, galleries, ordering, activation.
- **Enrollments** — view sign-ups per date, manually add a participant, send a bulk email to those registered.
- **Archive** — history of completed dates.
- **GDPR** — anonymize participants' personal data on request.

Any significant change to a date or removal of an enrollment **automatically notifies participants by email** — the communication takes care of itself.

## Key features

- ✅ **Live online enrollment** with protection against duplicate sign-ups.
- 📧 **Transactional emails** — confirmations, change/cancellation notices, bulk mailings.
- ⏰ **Automatic rules** — enrollment closes 24 hours before the start; past dates are archived.
- 👥 **Staff profiles** with bio and gallery, assigned to the relevant categories.
- 🖼️ **Photo galleries** for session types (with server-side image optimization).
- 🔗 **Social sharing** (Facebook / WhatsApp / copy link) with rich previews — dedicated Open Graph meta tags served to social-media crawlers.
- 🔍 **SEO** — a sitemap (`sitemap.xml`) and markup for search engines.
- 🛡️ **GDPR** — anonymization of participant data on request.
- 🌙 **Dark mode** in the club's visual identity (anthracite / black / orange).
- 🇵🇱 **Polish-only** — a consistent, single-language interface and communication.

## Security and privacy

- The organizer login is **hidden** — there's no login button on the public site; the panel is protected by an administrator role.
- Authentication is based on **JWT** (a short-lived access token plus refresh), with email verification and password reset.
- Abuse protection: **rate limiting** per IP address and account lockout after a series of failed login attempts.
- Participants' personal data can be **anonymized** at any time in line with GDPR.

## Under the hood (in brief)

The application consists of two parts in a shared repository:

- **Backend** — Java + Spring Boot, PostgreSQL (Flyway migrations), a Spring Security layer, transactional emails, caching, and image optimization. A REST API split into public, user, and administrator zones.
- **Frontend** — React + TypeScript + Vite, Tailwind CSS, React Query, and React Router. A responsive, accessible (a11y) interface with dark mode.

```
fire-academy-backend/    # Java / Spring Boot — API, enrollments, emails, security
fire-academy-frontend/   # React / TypeScript — public site + admin panel
fire-academy-hub/        # Runtime configuration (Docker Compose, env)
```

---

<div align="center">

**Fire Academy** · version `0.1.0` · © FIZJO4LIFE Sp. z o.o.

</div>

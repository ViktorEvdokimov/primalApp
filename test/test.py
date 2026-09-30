import os
import requests


requests.post(
	"https://api.mailgun.net/v3/sandbox75619e8b2176417088bab7c486c6a95b.mailgun.org/messages",
	auth=("api", os.getenv('API_KEY', '8a39d4a3917b12abafd1b2e5617675bb-7543e985-235edd768a39d4a3917b12abafd1b2e5617675bb-7543e985-235edd76')),
	data={"from": "Mailgun Sandbox <postmaster@sandbox75619e8b2176417088bab7c486c6a95b.mailgun.org>",
		"to": "Viktor Evdokimov <lxomlgmail.ru>",
		"subject": "Hello Viktor Evdokimov",
		"text": "Congratulations Viktor Evdokimov, you just sent an email with Mailgun! You are truly awesome!"})
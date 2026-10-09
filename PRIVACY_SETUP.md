# Portfolio Lab privacy setup

## Important
The repository and GitHub Pages website are currently PUBLIC. Never commit personal financial records, passwords or snapshot JSON files. The local snapshot importer runs in your browser only.

## For owner-only access
GitHub Pages on a public repository does not provide account login protection. Switching the repository to private may take down public Pages, but does not by itself establish authenticated site access. Use a hosting platform with identity-aware access control (for example Cloudflare Access, depending on plan and setup) or run the app entirely locally. Before entering actual sensitive financial transactions, restrict access with proper authentication; never rely on a client-side password stored in JavaScript.

## Local import
Download the financial JSON directly on the phone, open Portfolio Lab, select Private asset snapshot and import the JSON. The app stores it in browser localStorage. Export a backup before clearing browser data. Legacy sample transactions remain separate and should not be mistaken for actual holdings.

## Data integrity
Financial snapshot uses verified account valuation totals with mixed capture times. Employee shares and household housing/debt data are provisional; UNKNOWN is not zero. Purchase prices, lot basis, fees and historic FX are missing; P/L may not be computed from snapshot-only valuations. No broker API or live prices.

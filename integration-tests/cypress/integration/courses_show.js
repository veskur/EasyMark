describe("Course editor", () => {
	beforeEach(() => {
		cy.visit("/courses/59f29bcb-978a-48ed-a0ac-7d8425920cdf?debugChangeLogin=admin")
	})

	it("appears as expected", () => {
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("links to the grading editor", () => {
		cy.contains("Edit participants and grading...")
			.click()
		cy.url().should("eq", Cypress.config().baseUrl + "/courses/59f29bcb-978a-48ed-a0ac-7d8425920cdf/grading")
	})

	it("creates assignments", () => {
		cy.contains("Add...")
			.click()
			.next("form")
			.find("input[name=name]")
			.type("Memory complexity vs. computational complexity")
			.closest("form")
			.find("input[name=maxScore]")
			.type("25")
			.closest("form")
			.find("input[name=link]")
			.type("https://example.com")
			.closest("form")
			.contains("Add assignment")
			.click()
		cy.contains("Memory complexity vs. computational complexity")
			.snapshot()
			.closest("li")
			.next()
			.contains("Test")
	})

	it("reorders assignments", () => {
		cy.contains("Big O notation")
			.closest("li")
			.find('form[action$="/update-order?direction=up"] button')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Big O notation")
			.closest("li")
			.find('form[action$="/update-order?direction=down"] button')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("updates assignments", () => {
		cy.contains("Algorithmic complexity")
			.closest("li")
			.find('a[href*="/edit"]')
			.click()
		cy.get('a[href="/courses/59f29bcb-978a-48ed-a0ac-7d8425920cdf"]')
			.contains("Back")
		cy.contains("Name:")
			.next("input")
			.clear()
			.type("Algorithmic complexity UPDATED")
		cy.contains("External link:")
			.next("input")
			.type("https://example.com{enter}")
		cy.contains("Algorithmic complexity UPDATED")
			.snapshot()
	})

	it("deletes assignments", () => {
		cy.contains("Algorithmic complexity")
			.closest("li")
			.find('a[href*="/confirm-delete"]')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Yes, delete")
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("creates chapters", () => {
		cy.get("#chapterName")
			.type("Kotlin")
		cy.get("#dueDate")
			.type("2021-01-31")
		cy.get("#testRequired")
			.check()
		cy.contains("Add chapter")
			.click()
		cy.contains("Kotlin")
			.closest(".ordered-list-row")
			.contains("(~31/01/21)")
			.closest(".ordered-list-row")
			.next()
			.contains("Test")
	})

	it("reorders chapters", () => {
		cy.contains("Algorithms")
			.closest(".ordered-list-row")
			.find('form[action$="/update-order?direction=up"] button')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Algorithms")
			.closest(".ordered-list-row")
			.find('form[action$="/update-order?direction=down"] button')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("updates chapters", () => {
		cy.contains("Complexity")
			.closest(".ordered-list-row")
			.find('a[href*="/edit"]')
			.click()
		cy.get('a[href="/courses/59f29bcb-978a-48ed-a0ac-7d8425920cdf"]')
			.contains("Back")
		cy.contains("Name:")
			.next("input")
			.clear()
			.type("Complexity UPDATED")
		cy.contains("Save")
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("deletes chapters", () => {
		cy.contains("Complexity")
			.closest(".ordered-list-row")
			.find('a[href*="/confirm-delete"]')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Yes, delete")
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("updates the course", () => {
		cy.contains("Debug Course 2020/21")
			.next('a[href*="/edit"]')
			.click()
		cy.get('a[href="/courses/59f29bcb-978a-48ed-a0ac-7d8425920cdf"]')
			.contains("Back")
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Name:")
			.next("input")
			.type(" UPDATED{enter}")
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})

	it("deletes the course", () => {
		cy.contains("Debug Course 2020/21")
			.parent()
			.find('a[href*="/confirm-delete"]')
			.click()
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
		cy.reload()
		cy.contains("Yes, delete")
			.click()
		cy.url().should("eq", Cypress.config().baseUrl + "/")
		cy.get("main")
			.removeNondeterminism()
			.snapshot()
	})
})
